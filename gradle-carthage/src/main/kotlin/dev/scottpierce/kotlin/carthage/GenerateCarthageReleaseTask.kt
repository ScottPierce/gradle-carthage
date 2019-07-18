package dev.scottpierce.kotlin.carthage

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.IllegalStateException
import java.lang.RuntimeException
import java.lang.reflect.Type
import java.nio.file.Files
import java.nio.file.StandardCopyOption

open class GenerateCarthageReleaseTask : DefaultTask() {
  companion object {
    private val CARTHAGE_JSON_FILE_TYPE: Type by lazy { (object : TypeToken<Map<String, String>>() { }).type }
    private const val TEMP_ZIP_NAME = "carthageZip.zip"
  }

  /** The version String that represents this release. */
  @Input
  var versionString: String? = null
    set(value) {
      field = value

      if (value != null && !::outputZipFileName.isInitialized) {
        outputZipFileName = "$value.zip"
      }
    }

  /** A reference to the current release json file. */
  @Input
  lateinit var previousJsonFile: Any

  /** The base URL for where you will upload the generated zip file. */
  @Input
  lateinit var baseReleaseUrl: String

  /** List of framework files to be packaged into the release. */
  @Input
  lateinit var frameworks: List<File>

  /** The output file name for the zip file. */
  @OutputFile
  lateinit var outputZipFileName: String

  /** The output file name for the json file. */
  @OutputFile
  var outputJsonFileName: String = "releases.json"

  /** The directory where the json file and framework zip will be output. */
  @OutputDirectory
  var outputDir: File = File(project.buildDir, "carthage")

  /**
   * If set to false, then an error will be thrown if the versionString supplied has already been used for a release.
   */
  @Input
  var overwriteOldVersions: Boolean = false

  @TaskAction
  fun generate() {
    checkTaskParameters()

    val previousJson = previousJsonFile
    if (previousJson !is File && previousJson !is String) {
      throw IllegalArgumentException("Invalid argument given for 'previousJsonFile'. Must be a File, a path to a " +
              "file, or a url pointing to the previously generated json file. Currently it's a " +
              previousJson.javaClass.name)
    }

    val baseReleaseUrl = baseReleaseUrl.trim()
    val outputZipFileName = outputZipFileName
    val outputJsonFileName = outputJsonFileName

    if (!outputDir.exists()) {
      outputDir.mkdirs()
    }

    val zipFile = File(outputDir, outputZipFileName).also { if (it.exists()) it.deleteRecursively() }
    val jsonFile = File(outputDir, outputJsonFileName)

    val gson = GsonBuilder()
      .setPrettyPrinting()
      .create()

    val newReleases: LinkedHashMap<String, String> = run {
      val previousReleases = getPreviousJsonFromSource(gson)
      if (previousReleases.containsKey(versionString) && !overwriteOldVersions) {
        throw IllegalStateException(
          "Release called '$versionString' already exists. Please update the 'versionString' " +
                  "parameter, or set 'overwriteOldVersions' to 'true' (not recommended)."
        )
      }

      LinkedHashMap<String, String>(previousReleases.size + 1).also {
        it[versionString!!] = "$baseReleaseUrl${if (baseReleaseUrl.last() == '/') "" else "/"}$outputZipFileName"
        it.putAll(previousReleases)
      }
    }

    // Delete this after reading the file in case we are writing to the same place we are reading.
    // jsonFile.also { if (it.exists()) it.deleteRecursively() }

    // Create zip file
    run {
      val tempDir = File("${project.buildDir}/tmp/carthage")

      run { // Clean temp dir
        if (tempDir.exists()) {
          tempDir.deleteRecursively()
        }
        tempDir.mkdirs()
      }

      // Copy framework dirs into temp dir
      for (framework in frameworks) {
        val tempPath = File(tempDir, framework.name).toPath()
        val frameworkSource = framework.toPath()

        Files.walk(framework.toPath()).forEach { source ->
          Files.copy(source, tempPath.resolve(frameworkSource.relativize(source)), StandardCopyOption.REPLACE_EXISTING)
        }
      }

      val frameworksString = frameworks.joinToString(separator = " ") { it.name }

      println("Zipping Frameworks:")
      run {
        val command = "zip -r -X -g -T $TEMP_ZIP_NAME $frameworksString"
        val process = Runtime.getRuntime().exec(command, null, tempDir)

        val zipExitCode = process.waitFor()
        println(process.inputStream.bufferedReader().readText())

        if (zipExitCode != 0) {
          throw RuntimeException("Zipping failed:\n${process.errorStream.bufferedReader().readText()}")
        }
      }

      println("Zipping Completed")
      Files.copy(File(tempDir, TEMP_ZIP_NAME).toPath(), zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    // Write json file
    run {
      val jsonString = gson.toJson(newReleases)
      jsonFile.bufferedWriter().use { it.write(jsonString) }
    }
  }

  private fun checkTaskParameters() {
    val versionString = versionString ?: throw IllegalArgumentException("Must set 'versionString' parameter on task " +
            "before trying to generate a carthage release.")

    if (versionString.isBlank()) {
      throw IllegalArgumentException("Must set 'versionString' parameter to a non-blank String before trying to " +
              "generate a carthage release. The current value is '$versionString', which is invalid.")
    }

    if (!::previousJsonFile.isInitialized) {
      throw IllegalArgumentException("Must set 'previousJsonFile' parameter on task before trying to generate a " +
              "carthage release.")
    }

    if (!::baseReleaseUrl.isInitialized) {
      throw IllegalArgumentException("Must set 'baseReleaseUrl' parameter on task before trying to generate a " +
              "carthage release.")
    }

    if (baseReleaseUrl.isBlank()) {
      throw IllegalArgumentException("Must set 'baseReleaseUrl' parameter to a non-blank String before trying to " +
              "generate a carthage release. The current value is '$baseReleaseUrl', which is invalid.")
    }

    if (!::frameworks.isInitialized) {
      throw IllegalArgumentException("Must set 'frameworks' parameter on task before trying to generate a " +
              "carthage release.")
    }

    if (frameworks.isEmpty()) {
      throw IllegalArgumentException("Must set 'frameworks' parameter to a non-empty List before trying to " +
              "generate a carthage release.")
    }

    for (frameworkFile in frameworks) {
      if (!frameworkFile.exists()) {
        throw IllegalArgumentException("The framework at the file path '${frameworkFile.absolutePath}' does not exist.")
      }
    }
  }

  private fun getPreviousJsonFromSource(gson: Gson): Map<String, String> {
    val previousJsonFile = previousJsonFile

    var file: File? = null
    if (previousJsonFile is File) {
      if (!previousJsonFile.exists()) {
        throw FileNotFoundException("Previous carthage json file not found at file path " +
                "'${previousJsonFile.absolutePath}'. If this is your first release generation, create an empty file " +
                "at the path.")
      }
      file = previousJsonFile
    } else if (previousJsonFile is String) {
      val _file = File(previousJsonFile)

      if (_file.exists()) {
        file = _file
      }
    } else {
      throw IllegalArgumentException("'previousJsonFile' must be a File, a file path, or a URL.")
    }

    val jsonString: String? = if (file == null) {
      // Try a network request
      val response = OkHttpClient().newCall(
        Request.Builder()
          .get()
          .url(previousJsonFile as String)
          .build()
      ).execute()

      if (response.code < 200 || response.code >= 300) {
        throw IOException("Bad response code '${response.code}' from '$previousJsonFile'.")
      }

      response.body?.string() ?: throw IOException("No contents returned for request body from '$previousJsonFile'")
    } else {
      file.bufferedReader().readText()
    }

    return if (jsonString.isNullOrBlank()) {
      mapOf()
    } else {
      gson.fromJson(jsonString, CARTHAGE_JSON_FILE_TYPE)
    }
  }
}
