package top.kagg886.pmf.logger

/**
 * Generates a tagged Kermit logger extension for the annotated class.
 *
 * A non-empty [tag] is used verbatim. Otherwise, the processor uses the class's
 * fully qualified name.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class Logger(public val tag: String = "")
