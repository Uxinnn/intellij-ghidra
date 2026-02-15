package com.codingmates.ghidra.intellij.ide.runConfiguration

import com.codingmates.ghidra.intellij.ide.GhidraBundle
import com.intellij.execution.ui.DefaultJreSelector
import com.intellij.execution.ui.JrePathEditor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withPathToTextConvertor
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withTextToPathConvertor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.getCanonicalPath
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.ui.setEmptyState
import com.intellij.ui.PanelWithAnchor
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.Nullable
import javax.swing.JCheckBox
import javax.swing.JComponent
import kotlin.io.path.Path


class GhidraLauncherConfigurationEditor(project: Project) : SettingsEditor<GhidraLauncherConfiguration>(),
    PanelWithAnchor {

    private var anchorComponent: JComponent? = null
    private val ghidraPathDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
    private val ghidraPathField = TextFieldWithBrowseButton()
    private val jreEditor = JrePathEditor(DefaultJreSelector.projectSdk(project))
    private val argEditor = ExpandableTextField()
    private val isHeadless = JCheckBox()

    private val configPanel = panel {
        row(GhidraBundle.message("ghidra.facet.editor.installation")) {
            val title = GhidraBundle.message("ghidra.facet.editor.installation.dialog.title")
            ghidraPathDescriptor.withPathToTextConvertor(::getPresentablePath)
                .withTextToPathConvertor(::getCanonicalPath)
                .withTitle(title)
            ghidraPathField.addBrowseFolderListener(
                "Select Ghidra Directory",
                "Choose the Ghidra installation directory",
                project,
                ghidraPathDescriptor
            )
            cell(ghidraPathField)
                .applyToComponent { setEmptyState(GhidraBundle.message("ghidra.facet.editor.installation.empty")) }
                .align(AlignX.FILL)
        }
        row {
            cell(jreEditor).align(AlignX.FILL)
        }
        row("Arguments") {
            cell(argEditor).align(AlignX.FILL)
        }
        row("Use headless") {
            cell(isHeadless).align(AlignX.FILL)
        }
    }

    override fun applyEditorTo(configuration: GhidraLauncherConfiguration) {
        configuration.alternativeJrePath = jreEditor.jrePathOrName
        configuration.isAlternativeJrePathEnabled = jreEditor.isAlternativeJreSelected
        configuration.setArgs(argEditor.getText())
        configuration.setHeadless(isHeadless.isSelected)
        configuration.setGhidraPath(ghidraPathField.text)
        configuration.checkConfiguration()
    }

    override fun createEditor(): JComponent {
        return configPanel
    }

    override fun getAnchor(): JComponent? {
        return anchorComponent
    }

    override fun resetEditorFrom(s: GhidraLauncherConfiguration) {
        jreEditor.setPathOrName(s.alternativeJrePath, s.isAlternativeJrePathEnabled)
        argEditor.text = s.getArgs()
        isHeadless.isSelected = s.getHeadless()
        ghidraPathField.text = s.getGhidraPath()
    }

    override fun setAnchor(anchor: @Nullable JComponent?) {
        anchorComponent = anchor
        jreEditor.anchor = anchor
    }

    override fun disposeEditor() {
    }
}
