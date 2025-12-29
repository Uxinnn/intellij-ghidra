package com.codingmates.ghidra.intellij.ide.runConfiguration

import com.codingmates.ghidra.intellij.ide.facet.GhidraFacet
import com.intellij.execution.ExecutionException
import com.intellij.execution.application.BaseJavaApplicationCommandLineState
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.util.JavaParametersUtil
import org.jetbrains.annotations.NotNull
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries


class GhidraLauncherCommandLineState(
    environment: ExecutionEnvironment?,
    configuration: @NotNull GhidraLauncherConfiguration
) : BaseJavaApplicationCommandLineState<GhidraLauncherConfiguration>(environment, configuration) {
    override fun createJavaParameters(): JavaParameters {
        val project = configuration.project
        val javaParameters = JavaParameters()
        var jrePath: String? = null

        if (configuration.isAlternativeJrePathEnabled) {
            jrePath = configuration.alternativeJrePath
        }

        javaParameters.jdk = JavaParametersUtil.createProjectJdk(project, jrePath)
        javaParameters.mainClass = "ghidra.GhidraLauncher"
        if (configuration.getHeadless()) {
            javaParameters.programParametersList.add("ghidra.app.util.headless.AnalyzeHeadless")
        } else {
            javaParameters.programParametersList.add("ghidra.GhidraRun")
        }
        javaParameters.programParametersList.addParametersString(configuration.getArgs())
        javaParameters.vmParametersList.addAll(GHIDRA_CLI_OPTS)
        javaParameters.vmParametersList.add("-Dghidra.external.modules=${environment.project.basePath}")
        JavaParametersUtil.configureProject(
            project,
            javaParameters,
            JavaParameters.JDK_ONLY,
            jrePath
        )
        val facet = GhidraFacet.findAnyInProject(project)
        val ghidraHome = facet?.installationPath ?: throw ExecutionException(
            "Unable to find Ghidra root directory. " +
                    "Open the Ghidra facet and re-apply so all module libs are added."
        )
        javaParameters.classPath.add("$ghidraHome/Ghidra/Framework/Utility/lib/Utility.jar")
        val regex = Regex("log4j-.*\\.jar$")
        val s = Path("$ghidraHome/Ghidra/Framework/Generic/lib").listDirectoryEntries()
            .filter { path -> path.isRegularFile() && regex.matches(path.fileName.toString()) }
        s.forEach { path -> javaParameters.classPath.add(path.toAbsolutePath().toString()) }
        setupJavaParameters(javaParameters)
        val cp = javaParameters.classPath.pathList
        val hasUtility = cp.any { it.endsWith("/Framework/Utility/lib/Utility.jar") || it.endsWith("\\Framework\\Utility\\lib\\Utility.jar") }
        val hasLog4j = cp.any { it.contains("log4j", ignoreCase = true) }

        if (!hasUtility || !hasLog4j) {
            throw ExecutionException(
                "Ghidra classpath looks incomplete (Utility/log4j). " +
                    "Open the Ghidra facet and re-apply so all module libs are added."
            )
        }
        return javaParameters
    }

    companion object {
        private val GHIDRA_CLI_OPTS = listOf(
            "-Djava.system.class.loader=ghidra.GhidraClassLoader",
            "-Dfile.encoding=UTF8",
            "-Duser.country=US",
            "-Duser.language=en",
            "-Duser.variant=",
            "-Dsun.java2d.opengl=false",
            "-Djdk.tls.client.protocols=TLSv1.2,TLSv1.3",
            "-Dcpu.core.limit=",
            "-Dcpu.core.override=",
            "-Dfont.size.override=",
            "-Dpython.console.encoding=UTF-8",
            "-Xshare:off",
            "-Dsun.java2d.d3d=false",
            "-Dlog4j.skipJansi=true",
            "-XX:+ShowCodeDetailsInExceptionMessages",
        )
    }
}