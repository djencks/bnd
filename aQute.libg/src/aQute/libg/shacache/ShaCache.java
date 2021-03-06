package aQute.libg.shacache;

import java.io.*;
import java.util.regex.*;

import aQute.lib.io.*;
import aQute.libg.cryptography.*;

/**
 * Provide a standardized cache based on the SHA-1 of a file.
 */
public class ShaCache {
	static Pattern SHA_P = Pattern.compile("[A-F0-9]{40,40}", Pattern.CASE_INSENSITIVE);

	private final File root;

	/**
	 * Create a SHA-1 cache on a directory. @param root the directory
	 */
	public ShaCache(File root) {
		this.root = root;
		this.root.mkdirs();
		if (!this.root.isDirectory())
			throw new IllegalArgumentException("Cannot create shacache root directory " + root);
	}

	/**
	 * Return a stream that is associated with a SHA. If the SHA is not in the
	 * local cache, the given sources parameter can specify a way to get the
	 * content. @param sha the sha @param sources objects that can retrieve the
	 * original data @return the stream or null if not found.
	 */
	public InputStream getStream(String sha, ShaSource... sources) throws Exception {

		//
		// Must be a valid SHA otherwise could be used to traverse the file
		// system
		//

		if (!SHA_P.matcher(sha).matches())
			throw new IllegalArgumentException("Not a SHA");

		//
		// Get the file
		//

		File f = new File(root, sha);
		if (!f.isFile()) {

			//
			// Not found, try the sources
			//

			for (ShaSource s : sources) {
				try {
					InputStream in = s.get(sha);
					if (in == null)
						continue;

					//
					// If the source is a fast source we should
					// not cache it
					//

					if (s.isFast())
						return in;

					//
					// Create a unique temporary file
					// and copy it.
					//

					File tmp = IO.createTempFile(root, sha.toLowerCase(), ".shacache");
					IO.copy(in, tmp);
					String digest = SHA1.digest(tmp).asHex();
					if (digest.equalsIgnoreCase(sha)) {

						//
						// Atomic rename. So even if it is downloaded multiple
						// times we end up with one copy and the SHA makes it
						// unique with the content.
						//

						tmp.renameTo(f);
						break;
					}
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		//
		// Check if we succeeded
		//

		if (!f.isFile())
			return null;

		return new FileInputStream(f);
	}

	/**
	 * Small variation on the cache that returns a file instead of a
	 * stream @param sha the SHA-1 @param sources the inputs @return a file or
	 * null
	 */
	public File getFile(String sha, ShaSource... sources) throws Exception {
		//
		// Must be a valid SHA otherwise could be used to traverse the file
		// system
		//

		if (!SHA_P.matcher(sha).matches())
			throw new IllegalArgumentException("Not a SHA");

		//
		// See if we already got it
		//

		File f = new File(root, sha);
		if (f.isFile())
			return f;

		for (ShaSource s : sources) {
			try {
				InputStream in = s.get(sha);
				if (in != null) {
					File tmp = IO.createTempFile(root, sha.toLowerCase(), ".shacache");
					IO.copy(in, tmp);
					String digest = SHA1.digest(tmp).asHex();
					if (digest.equalsIgnoreCase(sha)) {
						tmp.renameTo(f);
						break;
					}
				}
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}

		if (!f.isFile())
			return null;
		return f;
	}

	/**
	 * Clean the cache
	 */

	public void purge() {
		IO.delete(root);
		root.mkdirs();
	}

	/**
	 * Get the root to the cache
	 */
	public File getRoot() {
		return root;
	}
}
