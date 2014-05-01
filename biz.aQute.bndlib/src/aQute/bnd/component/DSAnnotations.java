package aQute.bnd.component;

import java.util.*;

import aQute.bnd.header.*;
import aQute.bnd.osgi.*;
import aQute.bnd.service.*;

/**
 * Analyze the class space for any classes that have an OSGi annotation for DS.
 */
public class DSAnnotations implements AnalyzerPlugin {
	
	public static final String DSANNOTATIONS_EXTENSIONS = "-dsannotations-extensions";

	public boolean analyzeJar(Analyzer analyzer) throws Exception {
		Parameters header = OSGiHeader.parseHeader(analyzer.getProperty(Constants.DSANNOTATIONS));
		if (header.size() == 0)
			return false;

		Instructions instructions = new Instructions(header);
		Collection<Clazz> list = analyzer.getClassspace().values();
		String sc = analyzer.getProperty(Constants.SERVICE_COMPONENT);
		List<String> names = new ArrayList<String>();
		if (sc != null && sc.trim().length() > 0)
			names.add(sc);
		
		List<ExtensionReader> extensions = new ArrayList<ExtensionReader>();
		Parameters extHeader = OSGiHeader.parseHeader(analyzer.getProperty(DSANNOTATIONS_EXTENSIONS));
		Instructions extInstructions = new Instructions(extHeader);
		for (Instruction instruction: extInstructions.keySet()) {
			String extension = instruction.toString();
			try {
				Class< ? extends ExtensionReader> cl = Class.forName(extension).asSubclass(ExtensionReader.class);
				ExtensionReader reader = cl.newInstance();
				extensions.add(reader);
			}
			catch (ClassNotFoundException e) {
				analyzer.error("Could not load extension reader class", e);
			}
			catch (IllegalAccessException e) {
				analyzer.error("Could not create extension reader", e);
			}
			catch (InstantiationException e) {
				analyzer.error("Could not create extension reader", e);
			}
		}

		for (Clazz c: list) {
			for (Instruction instruction : instructions.keySet()) {

				if (instruction.matches(c.getFQN())) {
					if (instruction.isNegated())
						break;
					ComponentDef definition = AnnotationReader.getDefinition(c, analyzer, extensions);
					if (definition != null) {
						definition.sortReferences();
						definition.prepare(analyzer);
						String name = "OSGI-INF/" + analyzer.validResourcePath(definition.name, "Invalid component name") + ".xml";
						names.add(name);
						analyzer.getJar().putResource(name, new TagResource(definition.getTag()));
					}
				}
			}
		}
		sc = Processor.append(names.toArray(new String[names.size()]));
		analyzer.setProperty(Constants.SERVICE_COMPONENT, sc);
		return false;
	}

	@Override
	public String toString() {
		return "DSAnnotations";
	}
}
