package top.kagg886.pmf.roomdatabasegenerator

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.validate

private const val databasePackageName = "top.kagg886.pmf.database"
private const val daoPackageName = "$databasePackageName.dao"
private const val databaseName = "AppDatabase"
private const val entityName = "androidx.room3.Entity"
private const val daoName = "androidx.room3.Dao"

public class RoomDatabaseGeneratorProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        RoomDatabaseGeneratorProcessor(environment.codeGenerator)
}

/** Project-specific generator for top.kagg886.pmf.database.AppDatabase. */
private class RoomDatabaseGeneratorProcessor(
    private val codeGenerator: CodeGenerator,
) : SymbolProcessor {
    private var generated: Boolean = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()

        val daoDeclarations = resolver.getAllFiles()
            .filter { file ->
                file.packageName.asString().let { it == daoPackageName || it.startsWith("$daoPackageName.") }
            }
            .flatMap { it.declarations.recursiveClassDeclarations() }
            .toList()
        if (daoDeclarations.any { !it.validate() }) return daoDeclarations

        val entities = daoDeclarations
            .asSequence()
            .filter { it.classKind == ClassKind.CLASS }
            .filter { it.hasAnnotation(entityName) }
            .sortedBy { it.qualifiedName?.asString() }
            .toList()
        val daos = daoDeclarations
            .asSequence()
            .filter { it.classKind == ClassKind.INTERFACE }
            .filter { it.hasAnnotation(daoName) }
            .sortedBy { it.qualifiedName?.asString() }
            .toList()

        val daoFunctions = daos.map { dao -> dao to daoFunctionName(dao) }
        require(daoFunctions.map { it.second }.distinct().size == daoFunctions.size) {
            "Room DAO names must produce unique AppDatabase function names."
        }

        codeGenerator.createNewFile(
            dependencies = Dependencies(
                aggregating = true,
                *daoDeclarations.mapNotNull { it.containingFile }.distinct().toTypedArray(),
            ),
            packageName = databasePackageName,
            fileName = databaseName,
        ).bufferedWriter().use { output ->
            output.appendLine("package $databasePackageName")
            output.appendLine()
            output.appendLine("import androidx.room3.ConstructedBy")
            output.appendLine("import androidx.room3.Database")
            output.appendLine("import androidx.room3.RoomDatabase")
            output.appendLine("import androidx.room3.RoomDatabaseConstructor")
            output.appendLine("import top.kagg886.pmf.BuildConfig")
            output.appendLine()
            output.appendLine("@Database(")
            output.appendLine("    entities = [")
            entities.forEach { output.appendLine("        ${it.qualifiedName!!.asString()}::class,") }
            output.appendLine("    ],")
            output.appendLine("    version = BuildConfig.APP_VERSION_CODE,")
            output.appendLine(")")
            output.appendLine("@ConstructedBy(AppDatabaseConstructor::class)")
            output.appendLine("public abstract class AppDatabase : RoomDatabase() {")
            daoFunctions.forEach { (dao, functionName) ->
                output.appendLine("    public abstract fun $functionName(): ${dao.qualifiedName!!.asString()}")
            }
            output.appendLine("}")
            output.appendLine()
            output.appendLine("@Suppress(\"NO_ACTUAL_FOR_EXPECT\")")
            output.appendLine("public expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {")
            output.appendLine("    override fun initialize(): AppDatabase")
            output.appendLine("}")
            output.appendLine()
            output.appendLine("public expect fun databaseBuilder(): RoomDatabase.Builder<AppDatabase>")
        }
        generated = true
        return emptyList()
    }

    private fun daoFunctionName(dao: KSClassDeclaration): String {
        val name = dao.simpleName.asString()
        require(name.endsWith("Dao") && name.length > "Dao".length) {
            "@Dao interface ${dao.qualifiedName?.asString()} must end with Dao."
        }
        return name.removeSuffix("Dao").replaceFirstChar(Char::lowercaseChar) + "Dao"
    }

    private fun KSDeclaration.hasAnnotation(qualifiedName: String): Boolean = annotations.any {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName
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
