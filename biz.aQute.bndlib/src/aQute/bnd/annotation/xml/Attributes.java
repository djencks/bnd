package aQute.bnd.annotation.xml;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.CLASS)
@Target({
	ElementType.TYPE
})
public @interface Attributes {
	
	Attribute[] value();

}
