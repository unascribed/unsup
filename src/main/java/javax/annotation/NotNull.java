package javax.annotation;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(CLASS)
@Target({ TYPE, FIELD, PARAMETER, LOCAL_VARIABLE, TYPE_PARAMETER, TYPE_USE })
public @interface NotNull {

}
