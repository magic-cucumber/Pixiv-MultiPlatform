package top.kagg886.pmf.logger.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate

private const val loggerAnnotationName: String = "top.kagg886.pmf.logger.Logger"

public class LoggerProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        LoggerProcessor(environment.codeGenerator, environment.logger)
}

private class LoggerProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    private val generatedClasses: MutableSet<String> = mutableSetOf()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(loggerAnnotationName).toList()
        val deferred = symbols.filterNot(KSAnnotated::validate)

        symbols.filterIsInstance<KSClassDeclaration>()
            .filter(KSAnnotated::validate)
            .forEach(::generateLoggerExtension)

        symbols.filterNot { it is KSClassDeclaration }
            .forEach { logger.error("@Logger can only target a class declaration.", it) }

        return deferred
    }

    private fun generateLoggerExtension(declaration: KSClassDeclaration) {
        val qualifiedName = declaration.qualifiedName?.asString()
        if (qualifiedName == null) {
            logger.error("@Logger cannot be used on a local or anonymous class.", declaration)
            return
        }
        if (!isAccessibleFromGeneratedFile(declaration)) {
            logger.error("@Logger requires the annotated class and its enclosing classes to be neither private nor protected.", declaration)
            return
        }
        if (!generatedClasses.add(qualifiedName)) return

        val tag = declaration.annotations
            .first { annotation ->
                annotation.annotationType.resolve().declaration.qualifiedName?.asString() == loggerAnnotationName
            }
            .arguments
            .firstOrNull { argument -> argument.name?.asString() == "tag" }
            ?.value
            ?.let { it as? String }
            ?.takeIf(String::isNotEmpty)
            ?: qualifiedName
        val visibility = effectiveVisibility(declaration)
        val fileName = "LoggerExtension_" + qualifiedName.replace(Regex("[^A-Za-z0-9_]"), "_")

        codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = false, declaration.containingFile!!),
            packageName = declaration.packageName.asString(),
            fileName = fileName,
        ).bufferedWriter().use { output ->
            output.appendLine("package ${declaration.packageName.asString()}")
            output.appendLine()
            output.appendLine("$visibility val ${receiverType(declaration)}.logger: co.touchlab.kermit.Logger")
            output.appendLine("    get() = co.touchlab.kermit.Logger.withTag(\"${tag.escapeKotlinString()}\")")
        }
    }

    private fun isAccessibleFromGeneratedFile(declaration: KSClassDeclaration): Boolean {
        var current: KSDeclaration? = declaration
        while (current != null) {
            if (current.modifiers.contains(Modifier.PRIVATE) || current.modifiers.contains(Modifier.PROTECTED)) {
                return false
            }
            current = current.parentDeclaration
        }
        return true
    }

    private fun effectiveVisibility(declaration: KSClassDeclaration): String =
        if (generateSequence(declaration as KSDeclaration?) { it.parentDeclaration }
                .any { it.modifiers.contains(Modifier.INTERNAL) }
        ) {
            "internal"
        } else {
            "public"
        }

    private fun receiverType(declaration: KSClassDeclaration): String {
        val parents = generateSequence(declaration as KSDeclaration?) { it.parentDeclaration }
            .filterIsInstance<KSClassDeclaration>()
            .toList()
            .asReversed()
        return parents.mapIndexed { index, parent ->
            val isReceiver = index == parents.lastIndex
            val hasInnerChild = parents.getOrNull(index + 1)?.modifiers?.contains(Modifier.INNER) == true
            parent.simpleName.asString() + if (isReceiver || hasInnerChild) {
                parent.typeParameters.takeIf { it.isNotEmpty() }
                    ?.joinToString(prefix = "<", postfix = ">") { "*" }
                    .orEmpty()
            } else {
                ""
            }
        }
            .joinToString(separator = ".")
    }

    private fun String.escapeKotlinString(): String =
        replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
