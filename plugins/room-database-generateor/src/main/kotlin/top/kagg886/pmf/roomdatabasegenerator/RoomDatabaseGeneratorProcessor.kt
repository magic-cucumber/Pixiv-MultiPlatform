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
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.validate

private const val databasePackageName = "top.kagg886.pmf.database"
private const val entityName = "androidx.room3.Entity"
private const val daoName = "androidx.room3.Dao"

public class RoomDatabaseGeneratorProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        RoomDatabaseGeneratorProcessor(environment.codeGenerator)
}

/** Generates one Room database for every direct child package of the database package. */
private class RoomDatabaseGeneratorProcessor(
    private val codeGenerator: CodeGenerator,
) : SymbolProcessor {
    private var generated: Boolean = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()

        val databases = resolver.getAllFiles()
            .mapNotNull { file -> file.databasePackage()?.let { it to file } }
            .groupBy({ it.first }, { it.second })
            .map { (basePackage, files) -> databaseDefinition(basePackage, files) }
            .sortedBy { it.packageName }

        val declarations = databases.flatMap { it.declarations }
        if (declarations.any { !it.validate() }) return declarations

        val platformType = resolver.platformType()
        databases.forEach { database ->
            when (platformType) {
                "common" -> generateDatabase(database)
                "android", "jvm", "native" -> generatePlatformCreate(database, platformType)
                else -> error("Unsupported KSP platform type: $platformType")
            }
        }
        generated = true
        return emptyList()
    }

    private fun Resolver.platformType(): String = when {
        getClassDeclarationByName(getKSNameFromString("androidx.sqlite.driver.bundled.BundledSQLiteDriver")) != null -> "jvm"
        getClassDeclarationByName(getKSNameFromString("androidx.sqlite.driver.NativeSQLiteDriver")) != null -> "native"
        getClassDeclarationByName(getKSNameFromString("android.app.Application")) != null -> "android"
        else -> "common"
    }

    private fun databaseDefinition(packageName: String, files: List<KSFile>): DatabaseDefinition {
        val declarations = files.asSequence()
            .flatMap { it.declarations.recursiveClassDeclarations() }
            .toList()
        val entities = declarations.asSequence()
            .filter { it.classKind == ClassKind.CLASS && it.hasAnnotation(entityName) }
            .sortedBy { it.qualifiedName?.asString() }
            .toList()
        val daos = declarations.asSequence()
            .filter { it.classKind == ClassKind.INTERFACE && it.hasAnnotation(daoName) }
            .sortedBy { it.qualifiedName?.asString() }
            .toList()
        require(entities.isNotEmpty()) { "$packageName must contain at least one @Entity in its entity package." }
        require(daos.isNotEmpty()) { "$packageName must contain at least one @Dao in its dao package." }

        val daoFunctions = daos.map { dao -> dao to daoFunctionName(dao) }
        require(daoFunctions.map { it.second }.distinct().size == daoFunctions.size) {
            "Room DAO names in $packageName must produce unique database function names."
        }
        return DatabaseDefinition(
            packageName = packageName,
            className = "App${packageName.substringAfterLast('.').replaceFirstChar(Char::uppercaseChar)}Database",
            declarations = declarations,
            entities = entities,
            daoFunctions = daoFunctions,
        )
    }

    private fun generateDatabase(database: DatabaseDefinition) {
        val constructorName = "${database.className}Constructor"
        codeGenerator.createNewFile(
            dependencies = Dependencies(
                aggregating = true,
                *database.declarations.mapNotNull { it.containingFile }.distinct().toTypedArray(),
            ),
            packageName = database.packageName,
            fileName = database.className,
        ).bufferedWriter().use { output ->
            output.appendLine("package ${database.packageName}")
            output.appendLine()
            output.appendLine("import androidx.room3.ConstructedBy")
            output.appendLine("import androidx.room3.Database")
            output.appendLine("import androidx.room3.RoomDatabase")
            output.appendLine("import androidx.room3.RoomDatabaseConstructor")
            output.appendLine("import okio.Path")
            output.appendLine("import top.kagg886.pmf.BuildConfig")
            output.appendLine()
            output.appendLine("@Database(")
            output.appendLine("    entities = [")
            database.entities.forEach { output.appendLine("        ${it.qualifiedName!!.asString()}::class,") }
            output.appendLine("    ],")
            output.appendLine("    version = BuildConfig.APP_VERSION_CODE,")
            output.appendLine(")")
            output.appendLine("@ConstructedBy($constructorName::class)")
            output.appendLine("public abstract class ${database.className} : RoomDatabase() {")
            database.daoFunctions.forEach { (dao, functionName) ->
                output.appendLine("    public abstract fun $functionName(): ${dao.qualifiedName!!.asString()}")
            }
            output.appendLine()
            output.appendLine("    public companion object")
            output.appendLine("}")
            output.appendLine()
            output.appendLine("@Suppress(\"NO_ACTUAL_FOR_EXPECT\")")
            output.appendLine("public expect object $constructorName : RoomDatabaseConstructor<${database.className}> {")
            output.appendLine("    override fun initialize(): ${database.className}")
            output.appendLine("}")
            output.appendLine()
            output.appendLine("public expect fun ${database.className}.Companion.create(path: Path): ${database.className}")
        }
    }

    private fun generatePlatformCreate(database: DatabaseDefinition, platformType: String) {
        codeGenerator.createNewFile(
            dependencies = Dependencies(
                aggregating = true,
                *database.declarations.mapNotNull { it.containingFile }.distinct().toTypedArray(),
            ),
            packageName = database.packageName,
            fileName = "${database.className}.create.$platformType",
        ).bufferedWriter().use { output ->
            output.appendLine("package ${database.packageName}")
            output.appendLine()
            output.appendLine("import androidx.room3.Room")
            if (platformType == "jvm") {
                output.appendLine("import androidx.sqlite.driver.bundled.BundledSQLiteDriver")
            }
            if (platformType == "native") {
                output.appendLine("import androidx.sqlite.driver.NativeSQLiteDriver")
            }
            output.appendLine("import okio.Path")
            output.appendLine("import top.kagg886.pmf.database.util.commonBuilder")
            if (platformType == "android") {
                output.appendLine("import top.kagg886.pmf.util.currentApplication")
            }
            output.appendLine()
            when (platformType) {
                "android" -> {
                    output.appendLine("public actual fun ${database.className}.Companion.create(path: Path): ${database.className} = Room.databaseBuilder<${database.className}>(")
                    output.appendLine("    context = currentApplication(),")
                    output.appendLine("    name = path.toString(),")
                    output.appendLine(").commonBuilder().build()")
                }
                "jvm", "native" -> {
                    val driver = if (platformType == "jvm") "BundledSQLiteDriver" else "NativeSQLiteDriver"
                    output.appendLine("public actual fun ${database.className}.Companion.create(path: Path): ${database.className} =")
                    output.appendLine("    Room.databaseBuilder<${database.className}>(path.toString())")
                    output.appendLine("        .setDriver($driver())")
                    output.appendLine("        .commonBuilder()")
                    output.appendLine("        .build()")
                }
            }
        }
    }

    private fun KSFile.databasePackage(): String? {
        if (!filePath.replace('\\', '/').contains("/src/commonMain/kotlin/")) return null
        val packageName = packageName.asString()
        if (!packageName.startsWith("$databasePackageName.")) return null
        val segments = packageName.removePrefix("$databasePackageName.").split('.')
        if (segments.size < 2) return null
        return when (segments[1]) {
            "entity", "dao" -> "$databasePackageName.${segments.first()}"
            else -> null
        }
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

private data class DatabaseDefinition(
    val packageName: String,
    val className: String,
    val declarations: List<KSClassDeclaration>,
    val entities: List<KSClassDeclaration>,
    val daoFunctions: List<Pair<KSClassDeclaration, String>>,
)
