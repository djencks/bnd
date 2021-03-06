package aQute.bnd.osgi;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import aQute.bnd.osgi.Descriptors.Descriptor;
import aQute.bnd.osgi.Descriptors.PackageRef;
import aQute.bnd.osgi.Descriptors.TypeRef;
import aQute.lib.utf8properties.UTF8Properties;
import aQute.libg.generics.Create;

public class Clazz {

	static Pattern METHOD_DESCRIPTOR = Pattern.compile("(.*)\\)(.+)");

	public class ClassConstant {
		int				cname;
		public boolean	referred;

		public ClassConstant(int class_index) {
			this.cname = class_index;
		}

		public String getName() {
			return (String) pool[cname];
		}

		public String toString() {
			return "ClassConstant[" + getName() + "]";
		}
	}

	public static enum JAVA {
		JDK1_1(45, "JRE-1.1", "(&(osgi.ee=JavaSE)(version=1.1))"), //
		JDK1_2(46, "J2SE-1.2", "(&(osgi.ee=JavaSE)(version=1.2))"), //
		JDK1_3(47, "J2SE-1.3", "(&(osgi.ee=JavaSE)(version=1.3))"), //
		JDK1_4(48, "J2SE-1.4", "(&(osgi.ee=JavaSE)(version=1.4))"), //
		J2SE5(49, "J2SE-1.5", "(&(osgi.ee=JavaSE)(version=1.5))"), //
		J2SE6(50, "JavaSE-1.6", "(&(osgi.ee=JavaSE)(version=1.6))"), //
		OpenJDK7(51, "JavaSE-1.7", "(&(osgi.ee=JavaSE)(version=1.7))"), //
		OpenJDK8(52, "JavaSE-1.8", "(&(osgi.ee=JavaSE)(version=1.8))") {

			Map<String,Set<String>> profiles;

			public Map<String,Set<String>> getProfiles() throws IOException {
				if (profiles == null) {
					Properties p = new UTF8Properties();
					InputStream in = Clazz.class.getResourceAsStream("profiles-" + this + ".properties");
					try {
						p.load(in);
					}
					finally {
						in.close();
					}
					profiles = new HashMap<String,Set<String>>();
					for (Map.Entry<Object,Object> prop : p.entrySet()) {
						String list = (String) prop.getValue();
						Set<String> set = new HashSet<String>();
						for (String s : list.split("\\s*,\\s*")) {
							set.add(s);
						}
						profiles.put((String) prop.getKey(), set);
					}
				}
				return profiles;
			}
		}, //
		UNKNOWN(Integer.MAX_VALUE, "<>", null)//
		;

		final int major;
		final String ee;
		final String filter;

		JAVA(int major, String ee, String filter) {
			this.major = major;
			this.ee = ee;
			this.filter = filter;
		}

		static JAVA format(int n) {
			for (JAVA e : JAVA.values())
				if (e.major == n)
					return e;
			return UNKNOWN;
		}

		public int getMajor() {
			return major;
		}

		public boolean hasAnnotations() {
			return major >= J2SE5.major;
		}

		public boolean hasGenerics() {
			return major >= J2SE5.major;
		}

		public boolean hasEnums() {
			return major >= J2SE5.major;
		}

		public static JAVA getJava(int major, @SuppressWarnings("unused") int minor) {
			for (JAVA j : JAVA.values()) {
				if (j.major == major)
					return j;
			}
			return UNKNOWN;
		}

		public String getEE() {
			return ee;
		}

		public String getFilter() {
			return filter;
		}

		public Map<String,Set<String>> getProfiles() throws IOException {
			return null;
		}
	}

	public static enum QUERY {
		IMPLEMENTS, EXTENDS, IMPORTS, NAMED, ANY, VERSION, CONCRETE, ABSTRACT, PUBLIC, ANNOTATED, RUNTIMEANNOTATIONS, CLASSANNOTATIONS, DEFAULT_CONSTRUCTOR;

	}

	public final static EnumSet<QUERY> HAS_ARGUMENT = EnumSet.of(QUERY.IMPLEMENTS, QUERY.EXTENDS, QUERY.IMPORTS,
			QUERY.NAMED, QUERY.VERSION, QUERY.ANNOTATED);

	/**
	 * <pre> ACC_PUBLIC 0x0001 Declared public; may be accessed from outside its
	 * package. ACC_FINAL 0x0010 Declared final; no subclasses allowed.
	 * ACC_SUPER 0x0020 Treat superclass methods specially when invoked by the
	 * invokespecial instruction. ACC_INTERFACE 0x0200 Is an interface, not a
	 * class. ACC_ABSTRACT 0x0400 Declared abstract; may not be instantiated.
	 * </pre> @param mod
	 */

	// Declared public; may be accessed from outside its package.
	final static int	ACC_PUBLIC		= 0x0001;
	// Declared final; no subclasses allowed.
	final static int	ACC_FINAL		= 0x0010;
	// Treat superclass methods specially when invoked by the invokespecial
	// instruction.
	final static int	ACC_SUPER		= 0x0020;
	// Is an interface, not a class
	final static int	ACC_INTERFACE	= 0x0200;
	// Declared a thing not in the source code
	final static int	ACC_ABSTRACT	= 0x0400;
	final static int	ACC_SYNTHETIC	= 0x1000;
	final static int	ACC_ANNOTATION	= 0x2000;
	final static int	ACC_ENUM		= 0x4000;

	static protected class Assoc {
		Assoc(byte tag, int a, int b) {
			this.tag = tag;
			this.a = a;
			this.b = b;
		}

		byte	tag;
		int		a;
		int		b;

		public String toString() {
			return "Assoc[" + a + "," + b + "]";
		}
	}

	public abstract class Def {

		final int		access;
		Set<TypeRef>	annotations;

		public Def(int access) {
			this.access = access;
		}

		public int getAccess() {
			return access;
		}

		public boolean isEnum() {
			return (access & ACC_ENUM) != 0;
		}

		public boolean isPublic() {
			return Modifier.isPublic(access);
		}

		public boolean isAbstract() {
			return Modifier.isAbstract(access);
		}

		public boolean isProtected() {
			return Modifier.isProtected(access);
		}

		public boolean isFinal() {
			return Modifier.isFinal(access) || Clazz.this.isFinal();
		}

		public boolean isStatic() {
			return Modifier.isStatic(access);
		}

		public boolean isPrivate() {
			return Modifier.isPrivate(access);
		}

		public boolean isNative() {
			return Modifier.isNative(access);
		}

		public boolean isTransient() {
			return Modifier.isTransient(access);
		}

		public boolean isVolatile() {
			return Modifier.isVolatile(access);
		}

		public boolean isInterface() {
			return Modifier.isInterface(access);
		}

		public boolean isSynthetic() {
			return (access & ACC_SYNTHETIC) != 0;
		}

		void addAnnotation(Annotation a) {
			if (annotations == null)
				annotations = Create.set();
			annotations.add(analyzer.getTypeRef(a.getName().getBinary()));
		}

		public Collection<TypeRef> getAnnotations() {
			return annotations;
		}

		public TypeRef getOwnerType() {
			return className;
		}

		public abstract String getName();

		public abstract TypeRef getType();

		public abstract TypeRef[] getPrototype();

		public Object getClazz() {
			return Clazz.this;
		}
	}

	public class FieldDef extends Def {
		final String		name;
		final Descriptor	descriptor;
		String				signature;
		Object				constant;
		boolean				deprecated;

		public boolean isDeprecated() {
			return deprecated;
		}

		public void setDeprecated(boolean deprecated) {
			this.deprecated = deprecated;
		}

		public FieldDef(int access, String name, String descriptor) {
			super(access);
			this.name = name;
			this.descriptor = analyzer.getDescriptor(descriptor);
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public TypeRef getType() {
			return descriptor.getType();
		}

		public TypeRef getContainingClass() {
			return getClassName();
		}

		public Descriptor getDescriptor() {
			return descriptor;
		}

		public void setConstant(Object o) {
			this.constant = o;
		}

		public Object getConstant() {
			return this.constant;
		}

		// TODO change to use proper generics
		public String getGenericReturnType() {
			String use = descriptor.toString();
			if (signature != null)
				use = signature;

			Matcher m = METHOD_DESCRIPTOR.matcher(use);
			if (!m.matches())
				throw new IllegalArgumentException("Not a valid method descriptor: " + descriptor);

			String returnType = m.group(2);
			return objectDescriptorToFQN(returnType);
		}

		@Override
		public TypeRef[] getPrototype() {
			return null;
		}

		public String getSignature() {
			return signature;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	public class MethodDef extends FieldDef {
		public MethodDef(int access, String method, String descriptor) {
			super(access, method, descriptor);
		}

		public boolean isConstructor() {
			return name.equals("<init>") || name.equals("<clinit>");
		}

		@Override
		public TypeRef[] getPrototype() {
			return descriptor.getPrototype();
		}
	}

	public class TypeDef extends Def {
		TypeRef	type;
		boolean	interf;

		public TypeDef(TypeRef type, boolean interf) {
			super(Modifier.PUBLIC);
			this.type = type;
			this.interf = interf;
		}

		public TypeRef getReference() {
			return type;
		}

		public boolean getImplements() {
			return interf;
		}

		@Override
		public String getName() {
			if (interf)
				return "<implements>";
			return "<extends>";
		}

		@Override
		public TypeRef getType() {
			return type;
		}

		@Override
		public TypeRef[] getPrototype() {
			return null;
		}
	}

	final static byte SkipTable[] = { //
			0, // 0 non existent
			-1, // 1 CONSTANT_utf8 UTF 8, handled in
			// method
			-1, // 2
			4, // 3 CONSTANT_Integer
			4, // 4 CONSTANT_Float
			8, // 5 CONSTANT_Long (index +=2!)
			8, // 6 CONSTANT_Double (index +=2!)
			-1, // 7 CONSTANT_Class
			2, // 8 CONSTANT_String
			4, // 9 CONSTANT_FieldRef
			4, // 10 CONSTANT_MethodRef
			4, // 11 CONSTANT_InterfaceMethodRef
			4, // 12 CONSTANT_NameAndType
			-1, // 13 Not defined
			-1, // 14 Not defined
			3, // 15 CONSTANT_MethodHandle
			2, // 16 CONSTANT_MethodType
			-1, // 17 Not defined
			4, // 18 CONSTANT_InvokeDynamic
	};

	public static final Comparator<Clazz> NAME_COMPARATOR = new Comparator<Clazz>() {

		public int compare(Clazz a, Clazz b) {
			return a.className.compareTo(b.className);
		}

	};

	boolean	hasRuntimeAnnotations;
	boolean	hasClassAnnotations;
	boolean	hasDefaultConstructor;

	int						depth		= 0;
	Deque<ClassDataCollector> cds		= new LinkedList<>();

	TypeRef				className;
	Object				pool[];
	int					intPool[];
	Set<PackageRef>		imports		= Create.set();
	String				path;
	int					minor		= 0;
	int					major		= 0;
	int					innerAccess	= -1;
	int					accessx		= 0;
	String				sourceFile;
	Set<TypeRef>		xref;
	Set<TypeRef>		annotations;
	int					forName		= 0;
	int					class$		= 0;
	TypeRef[]			interfaces;
	TypeRef				zuper;
	ClassDataCollector	cd			= null;
	Resource			resource;
	FieldDef			last		= null;
	boolean				deprecated;
	Set<PackageRef>		api;
	final Analyzer		analyzer;
	String				classSignature;

	private boolean detectLdc;

	public Clazz(Analyzer analyzer, String path, Resource resource) {
		this.path = path;
		this.resource = resource;
		this.analyzer = analyzer;
	}

	public Set<TypeRef> parseClassFile() throws Exception {
		return parseClassFileWithCollector(null);
	}

	public Set<TypeRef> parseClassFile(InputStream in) throws Exception {
		return parseClassFile(in, null);
	}

	public Set<TypeRef> parseClassFileWithCollector(ClassDataCollector cd) throws Exception {
		InputStream in = resource.openInputStream();
		try {
			return parseClassFile(in, cd);
		}
		finally {
			in.close();
		}
	}

	public Set<TypeRef> parseClassFile(InputStream in, ClassDataCollector cd) throws Exception {
		DataInputStream din = new DataInputStream(in);
		try {
			cds.push(this.cd);
			this.cd = cd;
			return parseClassFile(din);
		}
		finally {
			this.cd = cds.pop();
			din.close();
		}
	}

	Set<TypeRef> parseClassFile(DataInputStream in) throws Exception {
		analyzer.trace("parseClassFile(): path=%s resource=%s", path, resource);

		++depth;
		xref = new HashSet<TypeRef>();

		boolean crawl = cd != null; // Crawl the byte code if we have a
		// collector
		int magic = in.readInt();
		if (magic != 0xCAFEBABE)
			throw new IOException("Not a valid class file (no CAFEBABE header)");

		minor = in.readUnsignedShort(); // minor version
		major = in.readUnsignedShort(); // major version
		if (cd != null)
			cd.version(minor, major);
		int count = in.readUnsignedShort();
		pool = new Object[count];
		intPool = new int[count];

		process: for (int poolIndex = 1; poolIndex < count; poolIndex++) {
			byte tag = in.readByte();
			switch (tag) {
				case 0 :
					break process;
				case 1 :
					constantUtf8(in, poolIndex);
					break;

				case 3 :
					constantInteger(in, poolIndex);
					break;

				case 4 :
					constantFloat(in, poolIndex);
					break;

				// For some insane optimization reason are
				// the long and the double two entries in the
				// constant pool. See 4.4.5
				case 5 :
					constantLong(in, poolIndex);
					poolIndex++;
					break;

				case 6 :
					constantDouble(in, poolIndex);
					poolIndex++;
					break;

				case 7 :
					constantClass(in, poolIndex);
					break;

				case 8 :
					constantString(in, poolIndex);
					break;

				case 9 : // Field ref
				case 10 : // Method ref
				case 11 : // Interface Method ref
					ref(in, poolIndex);
					break;

				// Name and Type
				case 12 :
					nameAndType(in, poolIndex, tag);
					break;

				case 18 : // TODO Invoke dynamic

					// We get the skip count for each record type
					// from the SkipTable. This will also automatically
					// abort when
				default :
					if (tag == 2)
						throw new IOException("Invalid tag " + tag);
					in.skipBytes(SkipTable[tag]);
					break;
			}
		}

		pool(pool, intPool);

		// All name& type and class constant records contain descriptors we must
		// treat
		// as references, though not API
		int index = -1;
		for (Object o : pool) {
			index++;
			if (o == null)
				continue;

			if (o instanceof Assoc) {
				Assoc assoc = (Assoc) o;
				switch (assoc.tag) {
					case 9 :
					case 10 :
					case 11 :
						classConstRef(assoc.a);
						break;

					case 12 :
						referTo(assoc.b, 0); // Descriptor
						break;
				}
			}
		}

		//
		// There is a bug in J8 compiler that leaves an
		// orphan class constant. So when we have a CC that
		// is not referenced by fieldrefs, method refs, or other
		// refs then we need to crawl the byte code.
		//
		index = -1;
		for (Object o : pool) {
			index++;
			if (o instanceof ClassConstant) {
				ClassConstant cc = (ClassConstant) o;
				if (cc.referred == false)
					detectLdc = true;
			}
		}

		/*
		 * Parse after the constant pool, code thanks to Hans Christian
		 * Falkenberg
		 */

		accessx = in.readUnsignedShort(); // access
		if (Modifier.isPublic(accessx))
			api = new HashSet<PackageRef>();

		int this_class = in.readUnsignedShort();
		className = analyzer.getTypeRef((String) pool[intPool[this_class]]);
		referTo(className, Modifier.PUBLIC);

		try {

			if (cd != null) {
				if (!cd.classStart(this))
					return null;
			}

			int super_class = in.readUnsignedShort();
			String superName = (String) pool[intPool[super_class]];
			if (superName != null) {
				zuper = analyzer.getTypeRef(superName);
			}

			if (zuper != null) {
				referTo(zuper, accessx);
				if (cd != null)
					cd.extendsClass(zuper);
			}

			int interfacesCount = in.readUnsignedShort();
			if (interfacesCount > 0) {
				interfaces = new TypeRef[interfacesCount];
				for (int i = 0; i < interfacesCount; i++) {
					interfaces[i] = analyzer.getTypeRef((String) pool[intPool[in.readUnsignedShort()]]);
					referTo(interfaces[i], accessx);
				}
				if (cd != null)
					cd.implementsInterfaces(interfaces);
			}

			int fieldsCount = in.readUnsignedShort();
			for (int i = 0; i < fieldsCount; i++) {
				int access_flags = in.readUnsignedShort(); // skip access flags
				int name_index = in.readUnsignedShort();
				int descriptor_index = in.readUnsignedShort();

				// Java prior to 1.5 used a weird
				// static variable to hold the com.X.class
				// result construct. If it did not find it
				// it would create a variable class$com$X
				// that would be used to hold the class
				// object gotten with Class.forName ...
				// Stupidly, they did not actively use the
				// class name for the field type, so bnd
				// would not see a reference. We detect
				// this case and add an artificial descriptor
				String name = pool[name_index].toString(); // name_index
				if (name.startsWith("class$") || name.startsWith("$class$")) {
					crawl = true;
				}
				if (cd != null)
					cd.field(last = new FieldDef(access_flags, name, pool[descriptor_index].toString()));

				referTo(descriptor_index, access_flags);
				doAttributes(in, ElementType.FIELD, false, access_flags);
			}

			//
			// Check if we have to crawl the code to find
			// the ldc(_w) <string constant> invokestatic Class.forName
			// if so, calculate the method ref index so we
			// can do this efficiently
			//
			if (crawl) {
				forName = findMethodReference("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;");
				class$ = findMethodReference(className.getBinary(), "class$", "(Ljava/lang/String;)Ljava/lang/Class;");
			} else if (major == 48) {
				forName = findMethodReference("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;");
				if (forName > 0) {
					crawl = true;
					class$ = findMethodReference(className.getBinary(), "class$",
							"(Ljava/lang/String;)Ljava/lang/Class;");
				}
			}

			// There are some serious changes in the
			// class file format. So we do not do any crawling
			// it has also become less important
			// however, jDK8 has a bug that leaves an orphan ClassConstnat
			// so if we have those, we need to also crawl the byte codes.
			// if (major >= JAVA.OpenJDK7.major)

			crawl |= detectLdc;

			//
			// Handle the methods
			//
			int methodCount = in.readUnsignedShort();
			for (int i = 0; i < methodCount; i++) {
				int access_flags = in.readUnsignedShort();
				int name_index = in.readUnsignedShort();
				int descriptor_index = in.readUnsignedShort();
				String name = pool[name_index].toString();
				String descriptor = pool[descriptor_index].toString();
				MethodDef mdef = null;
				if (cd != null) {
					mdef = new MethodDef(access_flags, name, descriptor);
					last = mdef;
					cd.method(mdef);
				}
				referTo(descriptor_index, access_flags);

				if ("<init>".equals(name)) {
					if (Modifier.isPublic(access_flags) && "()V".equals(descriptor)) {
						hasDefaultConstructor = true;
					}
					doAttributes(in, ElementType.CONSTRUCTOR, crawl, access_flags);
				} else {
					doAttributes(in, ElementType.METHOD, crawl, access_flags);
				}
			}
			if (cd != null)
				cd.memberEnd();
			last = null;

			doAttributes(in, ElementType.TYPE, false, accessx);

			//
			// Parse all the descriptors we found
			//

			Set<TypeRef> xref = this.xref;
			reset();
			return xref;
		}
		finally {
			if (cd != null)
				cd.classEnd();
		}
	}

	private void constantFloat(DataInputStream in, int poolIndex) throws IOException {
		if (cd != null)
			pool[poolIndex] = in.readFloat(); // ALU
		else
			in.skipBytes(4);
	}

	private void constantInteger(DataInputStream in, int poolIndex) throws IOException {
		intPool[poolIndex] = in.readInt();
		if (cd != null)
			pool[poolIndex] = intPool[poolIndex];
	}

	protected void pool(@SuppressWarnings("unused") Object[] pool, @SuppressWarnings("unused") int[] intPool) {}

	/**
	 * @param in @param poolIndex @param tag @throws IOException
	 */
	protected void nameAndType(DataInputStream in, int poolIndex, byte tag) throws IOException {
		int name_index = in.readUnsignedShort();
		int descriptor_index = in.readUnsignedShort();
		pool[poolIndex] = new Assoc(tag, name_index, descriptor_index);
	}

	/**
	 * @param in @param poolIndex @param tag @throws IOException
	 */
	private void ref(DataInputStream in, int poolIndex) throws IOException {
		int class_index = in.readUnsignedShort();
		int name_and_type_index = in.readUnsignedShort();
		pool[poolIndex] = new Assoc((byte) 10, class_index, name_and_type_index);
	}

	/**
	 * @param in @param poolIndex @throws IOException
	 */
	private void constantString(DataInputStream in, int poolIndex) throws IOException {
		int string_index = in.readUnsignedShort();
		intPool[poolIndex] = string_index;
	}

	/**
	 * @param in @param poolIndex @throws IOException
	 */
	protected void constantClass(DataInputStream in, int poolIndex) throws IOException {
		int class_index = in.readUnsignedShort();
		intPool[poolIndex] = class_index;
		ClassConstant c = new ClassConstant(class_index);
		pool[poolIndex] = c;
	}

	/**
	 * @param in @throws IOException
	 */
	protected void constantDouble(DataInputStream in, int poolIndex) throws IOException {
		if (cd != null)
			pool[poolIndex] = in.readDouble();
		else
			in.skipBytes(8);
	}

	/**
	 * @param in @throws IOException
	 */
	protected void constantLong(DataInputStream in, int poolIndex) throws IOException {
		if (cd != null) {
			pool[poolIndex] = in.readLong();
		} else
			in.skipBytes(8);
	}

	/**
	 * @param in @param poolIndex @throws IOException
	 */
	protected void constantUtf8(DataInputStream in, int poolIndex) throws IOException {
		// CONSTANT_Utf8

		String name = in.readUTF();
		pool[poolIndex] = name;
	}

	/**
	 * Find a method reference in the pool that points to the given class,
	 * methodname and descriptor. @param clazz @param methodname @param
	 * descriptor @return index in constant pool
	 */
	private int findMethodReference(String clazz, String methodname, String descriptor) {
		for (int i = 1; i < pool.length; i++) {
			if (pool[i] instanceof Assoc) {
				Assoc methodref = (Assoc) pool[i];
				if (methodref.tag == 10) {
					// Method ref
					int class_index = methodref.a;
					int class_name_index = intPool[class_index];
					if (clazz.equals(pool[class_name_index])) {
						int name_and_type_index = methodref.b;
						Assoc name_and_type = (Assoc) pool[name_and_type_index];
						if (name_and_type.tag == 12) {
							// Name and Type
							int name_index = name_and_type.a;
							int type_index = name_and_type.b;
							if (methodname.equals(pool[name_index])) {
								if (descriptor.equals(pool[type_index])) {
									return i;
								}
							}
						}
					}
				}
			}
		}
		return -1;
	}

	/**
	 * Called for each attribute in the class, field, or method. @param in The
	 * stream @param access_flags @throws Exception
	 */
	private void doAttributes(DataInputStream in, ElementType member, boolean crawl, int access_flags)
			throws Exception {
		int attributesCount = in.readUnsignedShort();
		for (int j = 0; j < attributesCount; j++) {
			// skip name CONSTANT_Utf8 pointer
			doAttribute(in, member, crawl, access_flags);
		}
	}

	/**
	 * Process a single attribute, if not recognized, skip it. @param in the
	 * data stream @param access_flags @throws Exception
	 */
	private void doAttribute(DataInputStream in, ElementType member, boolean crawl, int access_flags) throws Exception {
		int attribute_name_index = in.readUnsignedShort();
		String attributeName = (String) pool[attribute_name_index];
		long attribute_length = in.readInt();
		attribute_length &= 0xFFFFFFFF;
		if ("Deprecated".equals(attributeName)) {
			if (cd != null)
				cd.deprecated();
		} else if ("RuntimeVisibleAnnotations".equals(attributeName))
			doAnnotations(in, member, RetentionPolicy.RUNTIME, access_flags);
		else if ("RuntimeInvisibleAnnotations".equals(attributeName))
			doAnnotations(in, member, RetentionPolicy.CLASS, access_flags);
		else if ("RuntimeVisibleParameterAnnotations".equals(attributeName))
			doParameterAnnotations(in, member, RetentionPolicy.RUNTIME, access_flags);
		else if ("RuntimeInvisibleParameterAnnotations".equals(attributeName))
			doParameterAnnotations(in, member, RetentionPolicy.CLASS, access_flags);
		else if ("RuntimeVisibleTypeAnnotations".equals(attributeName))
			doTypeAnnotations(in, member, RetentionPolicy.RUNTIME, access_flags);
		else if ("RuntimeInvisibleTypeAnnotations".equals(attributeName))
			doTypeAnnotations(in, member, RetentionPolicy.CLASS, access_flags);
		else if ("InnerClasses".equals(attributeName))
			doInnerClasses(in);
		else if ("EnclosingMethod".equals(attributeName))
			doEnclosingMethod(in);
		else if ("SourceFile".equals(attributeName))
			doSourceFile(in);
		else if ("Code".equals(attributeName) && crawl)
			doCode(in);
		else if ("Signature".equals(attributeName))
			doSignature(in, member, access_flags);
		else if ("ConstantValue".equals(attributeName))
			doConstantValue(in);
		else if ("AnnotationDefault".equals(attributeName)) {
			Object value = doElementValue(in, member, RetentionPolicy.RUNTIME, cd != null, access_flags);
			if (last instanceof MethodDef) {
				((MethodDef) last).constant = value;
				cd.annotationDefault((MethodDef) last);
			}
		} else if ("Exceptions".equals(attributeName))
			doExceptions(in, access_flags);
		else {
			if (attribute_length > 0x7FFFFFFF) {
				throw new IllegalArgumentException("Attribute > 2Gb");
			}
			in.skipBytes((int) attribute_length);
		}
	}

	/**
	 * <pre> EnclosingMethod_attribute { u2 attribute_name_index; u4
	 * attribute_length; u2 class_index u2 method_index; } </pre> @param
	 * in @throws IOException
	 */
	private void doEnclosingMethod(DataInputStream in) throws IOException {
		int cIndex = in.readShort();
		int mIndex = in.readShort();
		classConstRef(cIndex);

		if (cd != null) {
			int nameIndex = intPool[cIndex];
			TypeRef cName = analyzer.getTypeRef((String) pool[nameIndex]);

			String mName = null;
			String mDescriptor = null;

			if (mIndex != 0) {
				Assoc nameAndType = (Assoc) pool[mIndex];
				mName = (String) pool[nameAndType.a];
				mDescriptor = (String) pool[nameAndType.b];
			}
			cd.enclosingMethod(cName, mName, mDescriptor);
		}
	}

	/**
	 * <pre> InnerClasses_attribute { u2 attribute_name_index; u4
	 * attribute_length; u2 number_of_classes; { u2 inner_class_info_index; u2
	 * outer_class_info_index; u2 inner_name_index; u2 inner_class_access_flags;
	 * } classes[number_of_classes]; } </pre> @param in @throws Exception
	 */
	private void doInnerClasses(DataInputStream in) throws Exception {
		int number_of_classes = in.readShort();
		for (int i = 0; i < number_of_classes; i++) {
			int inner_class_info_index = in.readShort();
			int outer_class_info_index = in.readShort();
			int inner_name_index = in.readShort();
			int inner_class_access_flags = in.readShort() & 0xFFFF;

			if (cd != null) {
				TypeRef innerClass = null;
				TypeRef outerClass = null;
				String innerName = null;

				if (inner_class_info_index != 0) {
					int nameIndex = intPool[inner_class_info_index];
					innerClass = analyzer.getTypeRef((String) pool[nameIndex]);
				}

				if (outer_class_info_index != 0) {
					int nameIndex = intPool[outer_class_info_index];
					outerClass = analyzer.getTypeRef((String) pool[nameIndex]);
				}

				if (inner_name_index != 0)
					innerName = (String) pool[inner_name_index];

				cd.innerClass(innerClass, outerClass, innerName, inner_class_access_flags);
			}
		}
	}

	/**
	 * Handle a signature <pre> Signature_attribute { u2 attribute_name_index;
	 * u4 attribute_length; u2 signature_index; } </pre> @param member @param
	 * access_flags
	 */

	void doSignature(DataInputStream in, ElementType member, int access_flags) throws IOException {
		int signature_index = in.readUnsignedShort();
		String signature = (String) pool[signature_index];
		try {

			parseDescriptor(signature, access_flags);
			if (last != null)
				last.signature = signature;

			if (cd != null)
				cd.signature(signature);

			if (member == ElementType.TYPE)
				classSignature = signature;

		}
		catch (Exception e) {
			new RuntimeException("Signature failed for" + signature, e);
		}
	}

	/**
	 * Handle a constant value call the data collector with it
	 */
	void doConstantValue(DataInputStream in) throws IOException {
		int constantValue_index = in.readUnsignedShort();
		if (cd == null)
			return;

		Object object = pool[constantValue_index];
		if (object == null)
			object = pool[intPool[constantValue_index]];

		last.constant = object;
		cd.constant(object);
	}

	void doExceptions(DataInputStream in, int access_flags) throws IOException {
		int exception_count = in.readUnsignedShort();
		for (int i = 0; i < exception_count; i++) {
			int index = in.readUnsignedShort();
			ClassConstant cc = (ClassConstant) pool[index];
			TypeRef clazz = analyzer.getTypeRef(cc.getName());
			referTo(clazz, access_flags);
		}
	}

	/**
	 * <pre> Code_attribute { u2 attribute_name_index; u4 attribute_length; u2
	 * max_stack; u2 max_locals; u4 code_length; u1 code[code_length]; u2
	 * exception_table_length; { u2 start_pc; u2 end_pc; u2 handler_pc; u2
	 * catch_type; } exception_table[exception_table_length]; u2
	 * attributes_count; attribute_info attributes[attributes_count]; }
	 * </pre> @param in @param pool @throws Exception
	 */
	private void doCode(DataInputStream in) throws Exception {
		/* int max_stack = */in.readUnsignedShort();
		/* int max_locals = */in.readUnsignedShort();
		int code_length = in.readInt();
		byte code[] = new byte[code_length];
		in.readFully(code);
		crawl(code);
		int exception_table_length = in.readUnsignedShort();
		for (int i = 0; i < exception_table_length; i++) {
			int start_pc = in.readUnsignedShort();
			int end_pc = in.readUnsignedShort();
			int handler_pc = in.readUnsignedShort();
			int catch_type = in.readUnsignedShort();
			classConstRef(catch_type);
		}
		doAttributes(in, ElementType.METHOD, false, 0);
	}

	/**
	 * We must find Class.forName references ... @param code
	 */
	protected void crawl(byte[] code) {
		ByteBuffer bb = ByteBuffer.wrap(code);
		bb.order(ByteOrder.BIG_ENDIAN);
		int lastReference = -1;

		while (bb.remaining() > 0) {
			int instruction = 0xFF & bb.get();
			switch (instruction) {
				case OpCodes.ldc :
					lastReference = 0xFF & bb.get();
					classConstRef(lastReference);
					break;

				case OpCodes.ldc_w :
					lastReference = 0xFFFF & bb.getShort();
					classConstRef(lastReference);
					break;

				case OpCodes.anewarray :
				case OpCodes.checkcast :
				case OpCodes.instanceof_ :
				case OpCodes.new_ : {
					int cref = 0xFFFF & bb.getShort();
					classConstRef(cref);
					lastReference = -1;
					break;
				}

				case OpCodes.multianewarray : {
					int cref = 0xFFFF & bb.getShort();
					classConstRef(cref);
					bb.get();
					lastReference = -1;
					break;
				}

				case OpCodes.invokespecial : {
					int mref = 0xFFFF & bb.getShort();
					if (cd != null)
						getMethodDef(0, mref);
					break;
				}

				case OpCodes.invokevirtual : {
					int mref = 0xFFFF & bb.getShort();
					if (cd != null)
						getMethodDef(0, mref);
					break;
				}

				case OpCodes.invokeinterface : {
					int mref = 0xFFFF & bb.getShort();
					if (cd != null)
						getMethodDef(0, mref);
					bb.get(); // read past the 'count' operand
					bb.get(); // read past the reserved space for future operand
					break;
				}

				case OpCodes.invokestatic : {
					int methodref = 0xFFFF & bb.getShort();
					if (cd != null)
						getMethodDef(0, methodref);

					if ((methodref == forName || methodref == class$) && lastReference != -1
							&& pool[intPool[lastReference]] instanceof String) {
						String fqn = (String) pool[intPool[lastReference]];
						if (!fqn.equals("class") && fqn.indexOf('.') > 0) {
							TypeRef clazz = analyzer.getTypeRefFromFQN(fqn);
							referTo(clazz, 0);
						}
						lastReference = -1;
					}
					break;
				}

					/*
					 * 3/5: opcode, indexbyte1, indexbyte2 or iinc, indexbyte1,
					 * indexbyte2, countbyte1, countbyte2
					 */
				case OpCodes.wide :
					int opcode = 0xFF & bb.get();
					bb.getShort(); // at least 3 bytes
					if (opcode == OpCodes.iinc)
						bb.getShort();
					break;

				case OpCodes.tableswitch :
					// Skip to place divisible by 4
					while ((bb.position() & 0x3) != 0)
						bb.get();
					/* int deflt = */
					bb.getInt();
					int low = bb.getInt();
					int high = bb.getInt();
					try {
						bb.position(bb.position() + (high - low + 1) * 4);
					}
					catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					lastReference = -1;
					break;

				case OpCodes.lookupswitch :
					// Skip to place divisible by 4
					while ((bb.position() & 0x3) != 0) {
						int n = bb.get();
						assert n == 0; // x
					}
					/* deflt = */
					int deflt = bb.getInt();
					int npairs = bb.getInt();
					bb.position(bb.position() + npairs * 8);
					lastReference = -1;
					break;

				default :
					lastReference = -1;
					bb.position(bb.position() + OpCodes.OFFSETS[instruction]);
			}
		}
	}

	private void doSourceFile(DataInputStream in) throws IOException {
		int sourcefile_index = in.readUnsignedShort();
		this.sourceFile = pool[sourcefile_index].toString();
	}

	private void doParameterAnnotations(DataInputStream in, ElementType member, RetentionPolicy policy,
			int access_flags) throws Exception {
		int num_parameters = in.readUnsignedByte();
		for (int p = 0; p < num_parameters; p++) {
			if (cd != null)
				cd.parameter(p);
			doAnnotations(in, member, policy, access_flags);
		}
	}

	private void doTypeAnnotations(DataInputStream in, ElementType member, RetentionPolicy policy, int access_flags)
			throws Exception {
		int num_annotations = in.readUnsignedShort();
		for (int p = 0; p < num_annotations; p++) {

			// type_annotation {
			// u1 target_type;
			// union {
			// type_parameter_target;
			// supertype_target;
			// type_parameter_bound_target;
			// empty_target;
			// method_formal_parameter_target;
			// throws_target;
			// localvar_target;
			// catch_target;
			// offset_target;
			// type_argument_target;
			// } target_info;
			// type_path target_path;
			// u2 type_index;
			// u2 num_element_value_pairs;
			// { u2 element_name_index;
			// element_value value;
			// } element_value_pairs[num_element_value_pairs];
			// }

			// Table 4.7.20-A. Interpretation of target_type values (Part 1)

			int target_type = in.readUnsignedByte();
			switch (target_type) {
				case 0x00 : // type parameter declaration of generic class or
							// interface
				case 0x01 : // type parameter declaration of generic method or
							// constructor
					//
					// type_parameter_target {
					// u1 type_parameter_index;
					// }
					in.skipBytes(1);
					break;

				case 0x10 : // type in extends clause of class or interface
							// declaration (including the direct superclass of
							// an anonymous class declaration), or in implements
							// clause of interface declaration
					// supertype_target {
					// u2 supertype_index;
					// }

					in.skipBytes(2);
					break;

				case 0x11 : // type in bound of type parameter declaration of
							// generic class or interface
				case 0x12 : // type in bound of type parameter declaration of
							// generic method or constructor
					// type_parameter_bound_target {
					// u1 type_parameter_index;
					// u1 bound_index;
					// }
					in.skipBytes(2);
					break;

				case 0x13 : // type in field declaration
				case 0x14 : // return type of method, or type of newly
							// constructed object
				case 0x15 : // receiver type of method or constructor
					break;

				case 0x16 : // type in formal parameter declaration of method,
							// constructor, or lambda expression
					// formal_parameter_target {
					// u1 formal_parameter_index;
					// }
					in.skipBytes(1);
					break;

				case 0x17 : // type in throws clause of method or constructor
					// throws_target {
					// u2 throws_type_index;
					// }
					in.skipBytes(2);
					break;

				case 0x40 : // type in local variable declaration
				case 0x41 : // type in resource variable declaration
					// localvar_target {
					// u2 table_length;
					// { u2 start_pc;
					// u2 length;
					// u2 index;
					// } table[table_length];
					// }
					int table_length = in.readUnsignedShort();
					in.skipBytes(table_length * 6);
					break;

				case 0x42 : // type in exception parameter declaration
					// catch_target {
					// u2 exception_table_index;
					// }
					in.skipBytes(2);
					break;

				case 0x43 : // type in instanceof expression
				case 0x44 : // type in new expression
				case 0x45 : // type in method reference expression using ::new
				case 0x46 : // type in method reference expression using
							// ::Identifier
					// offset_target {
					// u2 offset;
					// }
					in.skipBytes(2);
					break;

				case 0x47 : // type in cast expression
				case 0x48 : // type argument for generic constructor in new
							// expression or explicit constructor invocation
							// statement

				case 0x49 : // type argument for generic method in method
							// invocation expression
				case 0x4A : // type argument for generic constructor in method
							// reference expression using ::new
				case 0x4B : // type argument for generic method in method
							// reference expression using ::Identifier
					// type_argument_target {
					// u2 offset;
					// u1 type_argument_index;
					// }
					in.skipBytes(3);
					break;

			}

			// The value of the target_path item denotes precisely which part of
			// the type indicated by target_info is annotated. The format of the
			// type_path structure is specified in §4.7.20.2.
			//
			// type_path {
			// u1 path_length;
			// { u1 type_path_kind;
			// u1 type_argument_index;
			// } path[path_length];
			// }

			int path_length = in.readUnsignedByte();
			in.skipBytes(path_length * 2);

			//
			// Rest is identical to the normal annotations
			doAnnotation(in, member, policy, false, access_flags);
		}
	}

	private void doAnnotations(DataInputStream in, ElementType member, RetentionPolicy policy, int access_flags)
			throws Exception {
		int num_annotations = in.readUnsignedShort(); // # of annotations
		for (int a = 0; a < num_annotations; a++) {
			if (cd == null)
				doAnnotation(in, member, policy, false, access_flags);
			else {
				Annotation annotion = doAnnotation(in, member, policy, true, access_flags);
				cd.annotation(annotion);
			}
		}
	}

	// annotation {
	// u2 type_index;
	// u2 num_element_value_pairs; {
	// u2 element_name_index;
	// element_value value;
	// }
	// element_value_pairs[num_element_value_pairs];
	// }

	private Annotation doAnnotation(DataInputStream in, ElementType member, RetentionPolicy policy, boolean collect,
			int access_flags) throws IOException {
		int type_index = in.readUnsignedShort();
		if (annotations == null)
			annotations = new HashSet<TypeRef>();

		String typeName = (String) pool[type_index];
		TypeRef typeRef = null;
		if (typeName != null) {
			typeRef = analyzer.getTypeRef(typeName);
			annotations.add(typeRef);

			if (policy == RetentionPolicy.RUNTIME) {
				referTo(type_index, 0);
				hasRuntimeAnnotations = true;
				if (api != null && (Modifier.isPublic(access_flags) || Modifier.isProtected(access_flags)))
					api.add(typeRef.getPackageRef());
			} else {
				hasClassAnnotations = true;
			}
		}
		int num_element_value_pairs = in.readUnsignedShort();
		Map<String,Object> elements = null;
		for (int v = 0; v < num_element_value_pairs; v++) {
			int element_name_index = in.readUnsignedShort();
			String element = (String) pool[element_name_index];
			Object value = doElementValue(in, member, policy, collect, access_flags);
			if (collect) {
				if (elements == null)
					elements = new LinkedHashMap<String,Object>();
				elements.put(element, value);
			}
		}
		if (collect)
			return new Annotation(typeRef, elements, member, policy);
		return null;
	}

	private Object doElementValue(DataInputStream in, ElementType member, RetentionPolicy policy, boolean collect,
			int access_flags) throws IOException {
		char tag = (char) in.readUnsignedByte();
		switch (tag) {
			case 'B' : // Byte
			case 'C' : // Character
			case 'I' : // Integer
			case 'S' : // Short
				int const_value_index = in.readUnsignedShort();
				return intPool[const_value_index];

			case 'D' : // Double
			case 'F' : // Float
			case 's' : // String
			case 'J' : // Long
				const_value_index = in.readUnsignedShort();
				return pool[const_value_index];

			case 'Z' : // Boolean
				const_value_index = in.readUnsignedShort();
				return pool[const_value_index] == null || pool[const_value_index].equals(0) ? false : true;

			case 'e' : // enum constant
				int type_name_index = in.readUnsignedShort();
				if (policy == RetentionPolicy.RUNTIME) {
					referTo(type_name_index, 0);
					if (api != null && (Modifier.isPublic(access_flags) || Modifier.isProtected(access_flags))) {
						TypeRef name = analyzer.getTypeRef((String) pool[type_name_index]);
						api.add(name.getPackageRef());
					}
				}
				int const_name_index = in.readUnsignedShort();
				return pool[const_name_index];

			case 'c' : // Class
				int class_info_index = in.readUnsignedShort();
				TypeRef name = analyzer.getTypeRef((String) pool[class_info_index]);
				if (policy == RetentionPolicy.RUNTIME) {
					referTo(class_info_index, 0);
					if (api != null && (Modifier.isPublic(access_flags) || Modifier.isProtected(access_flags))) {
						api.add(name.getPackageRef());
					}
				}
				return name;

			case '@' : // Annotation type
				return doAnnotation(in, member, policy, collect, access_flags);

			case '[' : // Array
				int num_values = in.readUnsignedShort();
				Object[] result = new Object[num_values];
				for (int i = 0; i < num_values; i++) {
					result[i] = doElementValue(in, member, policy, collect, access_flags);
				}
				return result;

			default :
				throw new IllegalArgumentException("Invalid value for Annotation ElementValue tag " + tag);
		}
	}

	/**
	 * Add a new package reference. @param packageRef A '.' delimited package
	 * name
	 */
	void referTo(TypeRef typeRef, int modifiers) {
		if (xref != null)
			xref.add(typeRef);
		if (typeRef.isPrimitive())
			return;

		PackageRef packageRef = typeRef.getPackageRef();
		if (packageRef.isPrimitivePackage())
			return;

		imports.add(packageRef);

		if (api != null && (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)))
			api.add(packageRef);

		if (cd != null)
			cd.referTo(typeRef, modifiers);

	}

	void referTo(int index, int modifiers) {
		String descriptor = (String) pool[index];
		parseDescriptor(descriptor, modifiers);
	}

	/**
	 * This method parses a descriptor and adds the package of the descriptor to
	 * the referenced packages. The syntax of the descriptor is: <pre>
	 * descriptor ::= ( '(' reference * ')' )? reference reference ::= 'L'
	 * classname ( '&lt;' references '&gt;' )? ';' | 'B' | 'Z' | ... | '+' | '-'
	 * | '[' </pre> This methods uses heavy recursion to parse the descriptor
	 * and a roving pointer to limit the creation of string objects. @param
	 * descriptor The to be parsed descriptor @param rover The pointer to start
	 * at
	 */

	public void parseDescriptor(String descriptor, int modifiers) {
		// Some descriptors are weird, they start with a generic
		// declaration that contains ':', not sure what they mean ...
		int rover = 0;
		if (descriptor.charAt(0) == '<') {
			rover = parseFormalTypeParameters(descriptor, rover, modifiers);
		}

		if (descriptor.charAt(rover) == '(') {
			rover = parseReferences(descriptor, rover + 1, ')', modifiers);
			rover++;
		}
		parseReferences(descriptor, rover, (char) 0, modifiers);
	}

	/**
	 * Parse a sequence of references. A sequence ends with a given character or
	 * when the string ends. @param descriptor The whole descriptor. @param
	 * rover The index in the descriptor @param delimiter The end character or
	 * 0 @return the last index processed, one character after the delimeter
	 */
	int parseReferences(String descriptor, int rover, char delimiter, int modifiers) {
		int r = rover;
		while (r < descriptor.length() && descriptor.charAt(r) != delimiter) {
			r = parseReference(descriptor, r, modifiers);
		}
		return r;
	}

	/**
	 * Parse a single reference. This can be a single character or an object
	 * reference when it starts with 'L'. @param descriptor The
	 * descriptor @param rover The place to start @return The return index after
	 * the reference
	 */
	int parseReference(String descriptor, int rover, int modifiers) {
		int r = rover;
		char c = descriptor.charAt(r);
		while (c == '[')
			c = descriptor.charAt(++r);

		if (c == '<') {
			r = parseReferences(descriptor, r + 1, '>', modifiers);
		} else if (c == 'T') {
			// Type variable name
			r++;
			while (descriptor.charAt(r) != ';')
				r++;
		} else if (c == 'L') {
			StringBuilder sb = new StringBuilder();
			r++;
			while ((c = descriptor.charAt(r)) != ';') {
				if (c == '<') {
					r = parseReferences(descriptor, r + 1, '>', modifiers);
				} else
					sb.append(c);
				r++;
			}
			TypeRef ref = analyzer.getTypeRef(sb.toString());
			if (cd != null)
				cd.addReference(ref);

			referTo(ref, modifiers);
		} else {
			if ("+-*BCDFIJSZV".indexOf(c) < 0)
				;// System.err.println("Should not skip: " + c);
		}

		// this skips a lot of characters
		// [, *, +, -, B, etc.

		return r + 1;
	}

	/**
	 * FormalTypeParameters @param descriptor @param index @return
	 */
	private int parseFormalTypeParameters(String descriptor, int index, int modifiers) {
		index++;
		while (descriptor.charAt(index) != '>') {
			// Skip IDENTIFIER
			index = descriptor.indexOf(':', index) + 1;
			if (index == 0)
				throw new IllegalArgumentException("Expected IDENTIFIER: " + descriptor);

			// ClassBound? InterfaceBounds

			char c = descriptor.charAt(index);

			if (c == '[') {
				c = descriptor.charAt(++index);
			}

			// Class Bound?
			if (c == 'L' || c == 'T') {
				index = parseReference(descriptor, index, modifiers); // class
																		// reference
				c = descriptor.charAt(index);
			} else {
				index++;
			}

			// Interface Bounds
			while (c == ':') {
				index++;
				index = parseReference(descriptor, index, modifiers);
				c = descriptor.charAt(index);
			} // for each interface

		} // for each formal parameter
		return index + 1; // skip >
	}

	public Set<PackageRef> getReferred() {
		return imports;
	}

	public String getAbsolutePath() {
		return path;
	}

	public String getSourceFile() {
		return sourceFile;
	}

	/**
	 * .class construct for different compilers sun 1.1 Detect static variable
	 * class$com$acme$MyClass 1.2 " 1.3 " 1.4 " 1.5 ldc_w (class) 1.6 " eclipse
	 * 1.1 class$0, ldc (string), invokestatic Class.forName 1.2 " 1.3 " 1.5 ldc
	 * (class) 1.6 " 1.5 and later is not an issue, sun pre 1.5 is easy to
	 * detect the static variable that decodes the class name. For eclipse, the
	 * class$0 gives away we have a reference encoded in a string.
	 * compilerversions/compilerversions.jar contains test versions of all
	 * versions/compilers.
	 */

	public void reset() {
		if (--depth == 0) {
			pool = null;
			intPool = null;
			xref = null;
		}
	}

	public boolean is(QUERY query, Instruction instr, Analyzer analyzer) throws Exception {
		switch (query) {
			case ANY :
				return true;

			case NAMED :
				if (instr.matches(getClassName().getDottedOnly()))
					return !instr.isNegated();
				return false;

			case VERSION :
				String v = major + "." + minor;
				if (instr.matches(v))
					return !instr.isNegated();
				return false;

			case IMPLEMENTS :
				for (int i = 0; interfaces != null && i < interfaces.length; i++) {
					if (instr.matches(interfaces[i].getDottedOnly()))
						return !instr.isNegated();
				}
				break;

			case EXTENDS :
				if (zuper == null)
					return false;

				if (instr.matches(zuper.getDottedOnly()))
					return !instr.isNegated();
				break;

			case PUBLIC :
				return Modifier.isPublic(accessx);

			case CONCRETE :
				return !Modifier.isAbstract(accessx);

			case ANNOTATED :
				if (annotations == null)
					return false;

				for (TypeRef annotation : annotations) {
					if (instr.matches(annotation.getFQN()))
						return !instr.isNegated();
				}

				return false;

			case RUNTIMEANNOTATIONS :
				return hasRuntimeAnnotations;
			case CLASSANNOTATIONS :
				return hasClassAnnotations;

			case ABSTRACT :
				return Modifier.isAbstract(accessx);

			case IMPORTS :
				for (PackageRef imp : imports) {
					if (instr.matches(imp.getFQN()))
						return !instr.isNegated();
				}
				break;
			case DEFAULT_CONSTRUCTOR :
				return hasPublicNoArgsConstructor();
		}

		if (zuper == null)
			return false;

		Clazz clazz = analyzer.findClass(zuper);
		if (clazz == null) {
			analyzer.warning("While traversing the type tree while searching %s on %s cannot find class %s", query,
					this, zuper);
			return false;
		}

		return clazz.is(query, instr, analyzer);
	}

	@Override
	public String toString() {
		return className.getFQN();
	}

	/**
	 * Called when crawling the byte code and a method reference is found
	 */
	void getMethodDef(int access, int methodRefPoolIndex) {
		if (methodRefPoolIndex == 0)
			return;

		Object o = pool[methodRefPoolIndex];
		if (o != null && o instanceof Assoc) {
			Assoc assoc = (Assoc) o;
			if (assoc.tag == 10) {
				int string_index = intPool[assoc.a];
				TypeRef className = analyzer.getTypeRef((String) pool[string_index]);
				int name_and_type_index = assoc.b;
				Assoc name_and_type = (Assoc) pool[name_and_type_index];
				if (name_and_type.tag == 12) {
					// Name and Type
					int name_index = name_and_type.a;
					int type_index = name_and_type.b;
					String method = (String) pool[name_index];
					String descriptor = (String) pool[type_index];
					cd.referenceMethod(access, className, method, descriptor);
				} else
					throw new IllegalArgumentException(
							"Invalid class file (or parsing is wrong), assoc is not type + name (12)");
			} else
				throw new IllegalArgumentException(
						"Invalid class file (or parsing is wrong), Assoc is not method ref! (10)");
		} else
			throw new IllegalArgumentException(
					"Invalid class file (or parsing is wrong), Not an assoc at a method ref");
	}

	public boolean isPublic() {
		return Modifier.isPublic(accessx);
	}

	public boolean isProtected() {
		return Modifier.isProtected(accessx);
	}

	public boolean isEnum() {
		return zuper != null && zuper.getBinary().equals("java/lang/Enum");
	}

	public JAVA getFormat() {
		return JAVA.format(major);

	}

	public static String objectDescriptorToFQN(String string) {
		if ((string.startsWith("L") || string.startsWith("T")) && string.endsWith(";"))
			return string.substring(1, string.length() - 1).replace('/', '.');

		switch (string.charAt(0)) {
			case 'V' :
				return "void";
			case 'B' :
				return "byte";
			case 'C' :
				return "char";
			case 'I' :
				return "int";
			case 'S' :
				return "short";
			case 'D' :
				return "double";
			case 'F' :
				return "float";
			case 'J' :
				return "long";
			case 'Z' :
				return "boolean";
			case '[' : // Array
				return objectDescriptorToFQN(string.substring(1)) + "[]";
		}
		throw new IllegalArgumentException("Invalid type character in descriptor " + string);
	}

	public static String unCamel(String id) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < id.length(); i++) {
			char c = id.charAt(i);
			if (c == '_' || c == '$' || c == '.') {
				if (out.length() > 0 && !Character.isWhitespace(out.charAt(out.length() - 1)))
					out.append(' ');
				continue;
			}

			int n = i;
			while (n < id.length() && Character.isUpperCase(id.charAt(n))) {
				n++;
			}
			if (n == i)
				out.append(id.charAt(i));
			else {
				boolean tolower = (n - i) == 1;
				if (i > 0 && !Character.isWhitespace(out.charAt(out.length() - 1)))
					out.append(' ');

				for (; i < n;) {
					if (tolower)
						out.append(Character.toLowerCase(id.charAt(i)));
					else
						out.append(id.charAt(i));
					i++;
				}
				i--;
			}
		}
		if (id.startsWith("."))
			out.append(" *");
		out.replace(0, 1, Character.toUpperCase(out.charAt(0)) + "");
		return out.toString();
	}

	public boolean isInterface() {
		return Modifier.isInterface(accessx);
	}

	public boolean isAbstract() {
		return Modifier.isAbstract(accessx);
	}

	public boolean hasPublicNoArgsConstructor() {
		return hasDefaultConstructor;
	}

	public int getAccess() {
		if (innerAccess == -1)
			return accessx;
		return innerAccess;
	}

	public TypeRef getClassName() {
		return className;
	}

	/**
	 * To provide an enclosing instance @param access @param name @param
	 * descriptor @return
	 */
	public MethodDef getMethodDef(int access, String name, String descriptor) {
		return new MethodDef(access, name, descriptor);
	}

	public TypeRef getSuper() {
		return zuper;
	}

	public String getFQN() {
		return className.getFQN();
	}

	public TypeRef[] getInterfaces() {
		return interfaces;
	}

	public void setInnerAccess(int access) {
		innerAccess = access;
	}

	public boolean isFinal() {
		return Modifier.isFinal(accessx);
	}

	public void setDeprecated(boolean b) {
		deprecated = b;
	}

	public boolean isDeprecated() {
		return deprecated;
	}

	public boolean isAnnotation() {
		return (accessx & ACC_ANNOTATION) != 0;
	}

	public Set<PackageRef> getAPIUses() {
		if (api == null)
			return Collections.emptySet();
		return api;
	}

	public Clazz.TypeDef getExtends(TypeRef type) {
		return new TypeDef(type, false);
	}

	public Clazz.TypeDef getImplements(TypeRef type) {
		return new TypeDef(type, true);
	}

	private void classConstRef(int lastReference) {
		Object o = pool[lastReference];
		if (o == null)
			return;

		if (o instanceof ClassConstant) {
			ClassConstant cc = (ClassConstant) o;
			if (cc.referred)
				return;
			cc.referred = true;
			String name = cc.getName();
			if (name != null) {
				TypeRef tr = analyzer.getTypeRef(name);
				referTo(tr, 0);
			}
		}

	}

	public String getClassSignature() {
		return classSignature;
	}
}
