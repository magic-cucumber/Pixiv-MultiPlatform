import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import net.mamoe.yamlkt.Yaml
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@Serializable
enum class I18nLanguage(
    val yamlLang: String,
    val kotlinLang: String,
) {
    @SerialName("en_us")
    EN_US("en_us", "en"),

    @SerialName("zh_cn")
    ZH_CN("zh_cn", "zh-rCN"),
    ;

    val composeResourceDirectory: String
        get() = if (this == EN_US) "values" else "values-$kotlinLang"
}

@Serializable
@XmlSerialName("resources", "", "")
private data class AndroidStringResources(
    val strings: List<AndroidStringResource>,
)

@Serializable
@XmlSerialName("string", "", "")
private data class AndroidStringResource(
    @XmlElement(false)
    val name: String,
    @XmlValue(true)
    val value: String,
)

@CacheableTask
abstract class GenerateI18nComposeResourcesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val yamlFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val translations = linkedMapOf<String, Map<I18nLanguage, String>>()
        val translationsSerializer = MapSerializer(
            String.serializer(),
            MapSerializer(I18nLanguage.serializer(), String.serializer()),
        )

        yamlFiles.asFileTree.files.sortedBy { it.invariantSeparatorsPath }.forEach { file ->
            val yaml = Yaml.Default.decodeFromString(translationsSerializer, file.readText())
            yaml.forEach { (id, values) ->
                require(STRING_ID.matches(id)) {
                    "Invalid i18n string id '$id' in ${file.path}"
                }
                check(translations.put(id, values) == null) {
                    "Duplicate i18n string id '$id' in ${file.path}"
                }
            }
        }

        translations.forEach { (id, values) ->
            require(I18nLanguage.EN_US in values) {
                "Missing ${I18nLanguage.EN_US.yamlLang} translation for '$id'"
            }
        }

        outputDirectory.get().asFile.deleteRecursively()
        I18nLanguage.entries.forEach { language ->
            val resources = translations.mapNotNull { (id, values) ->
                values[language]?.let { AndroidStringResource(id, it) }
            }
            if (resources.isNotEmpty()) {
                outputDirectory.get().file(language.composeResourceDirectory).asFile.apply { mkdirs() }
                    .resolve("strings.xml")
                    .writeText(ANDROID_XML.encodeToString(AndroidStringResources(resources), prefix = null))
            }
        }
    }

    private companion object {
        val STRING_ID = Regex("[A-Za-z_][A-Za-z0-9_.-]*")
        val ANDROID_XML = XML.recommended_1_0()
    }
}
