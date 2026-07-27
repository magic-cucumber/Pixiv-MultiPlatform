package top.kagg886.pmf.navigationserializer

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.validate
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration

private const val serializableNavKeyName = "top.kagg886.pmf.util.nav3.SerializableNavKey"
private const val generatedPackageName = "top.kagg886.pmf"
private const val generatedFileName = "ApplicationNavSerializerModule"

public class NavigationSerializerModuleProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        NavigationSerializerModuleProcessor(environment.codeGenerator, environment.logger)
}

private class NavigationSerializerModuleProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    private var generated: Boolean = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()

        val serializableNavKey = resolver.getClassDeclarationByName(
            resolver.getKSNameFromString(serializableNavKeyName),
        ) ?: return emptyList()
        val routes = resolver.getAllFiles()
            .flatMap { it.declarations.recursiveClassDeclarations() }
            .filter { it.classKind == ClassKind.CLASS || it.classKind == ClassKind.OBJECT }
            .filterNot { it.isAbstract() || it.typeParameters.isNotEmpty() }
            .filter { declaration ->
                declaration.getAllSuperTypes().any {
                    it.declaration.qualifiedName?.asString() == serializableNavKey.qualifiedName?.asString()
                }
            }
            .sortedBy { it.qualifiedName?.asString() }
            .toList()

        if (routes.any { !it.validate() }) return routes
        if (routes.isEmpty()) {
            logger.warn("No SerializableNavKey subclasses were found; generating an empty module.")
        }

        codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = true, *resolver.getAllFiles().toList().toTypedArray()),
            packageName = generatedPackageName,
            fileName = generatedFileName,
        ).bufferedWriter().use { output ->
            output.appendLine("package $generatedPackageName")
            output.appendLine()
            output.appendLine("import kotlinx.serialization.modules.SerializersModule")
            output.appendLine("import kotlinx.serialization.modules.polymorphic")
            output.appendLine("import kotlinx.serialization.modules.subclass")
            output.appendLine("import $serializableNavKeyName")
            output.appendLine()
            output.appendLine("public val ApplicationNavSerializerModule: SerializersModule = SerializersModule {")
            output.appendLine("    polymorphic(SerializableNavKey::class) {")
            routes.forEach { route ->
                val name = route.qualifiedName?.asString()
                    ?: error("SerializableNavKey subclasses must have a qualified name.")
                output.appendLine("        subclass($name::class, $name.serializer())")
            }
            output.appendLine("    }")
            output.appendLine("}")
        }
        generated = true
        return emptyList()
    }

    private fun Sequence<KSDeclaration>.recursiveClassDeclarations(): Sequence<KSClassDeclaration> = sequence {
        for (declaration in this@recursiveClassDeclarations) {
            if (declaration is KSClassDeclaration) {
                yield(declaration)
                yieldAll(declaration.declarations.recursiveClassDeclarations())
            }
        }
    }
}
