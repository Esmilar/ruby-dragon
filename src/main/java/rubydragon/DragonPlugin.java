// SPDX-License-Identifier: Apache-2.0

/*
 * Copyright 2021-2023 Joel E. Anderson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rubydragon;

import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.swing.ImageIcon;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

import ghidra.app.plugin.ProgramPlugin;
import ghidra.app.plugin.core.console.CodeCompletion;
import ghidra.app.plugin.core.interpreter.InterpreterConnection;
import ghidra.framework.Application;
import ghidra.framework.options.ToolOptions;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.model.listing.Program;
import ghidra.program.util.ProgramLocation;
import ghidra.program.util.ProgramSelection;
import ghidra.util.HelpLocation;
import ghidra.util.xml.XmlUtilities;
import resources.ResourceManager;

/**
 * A plugin for RubyDragon that provides an interactive interpreter for a chosen
 * language.
 *
 * This abstract class takes care of a number of boilerplate tasks that should
 * be common across all plugins.
 */
public abstract class DragonPlugin extends ProgramPlugin implements InterpreterConnection {

	/**
	 * The name of the auto import manifest data file.
	 *
	 * @since 2.2.0
	 */
	public static String AUTO_IMPORT_FILENAME = "auto-import.xml";

	/**
	 * The name of the options category used by DragonPlugins.
	 *
	 * @since 2.2.0
	 */
	public static String OPTION_CATEGORY_NAME = "Ruby Dragon Interpreters";

	/**
	 * Runs the provided action for each import in the preload manifest.
	 *
	 * @param action The action called for each import listed in the preload
	 *               manifest, with the argument being the fully qualified class
	 *               name: the package and class name joined with a '.' character.
	 *
	 * @throws IOException   if the preload manifest file couldn't be opened.
	 * @throws JDOMException if the preload manifest xml was malformed.
	 *
	 * @since 2.2.0
	 */
	public static void forEachAutoImport(Consumer<String> action) throws JDOMException, IOException {
		forEachAutoImport((packageName, className) -> {
			action.accept(packageName + "." + className);
		});
	}

	/**
	 * Runs the provided action for each import in the preload manifest.
	 *
	 * @param action The action called for each import listed in the preload
	 *               manifest, with the arguments being the package name and class
	 *               name, respectively.
	 *
	 * @throws IOException   if the preload manifest file couldn't be opened.
	 * @throws JDOMException if the preload manifest xml was malformed.
	 *
	 * @since 2.2.0
	 */
	public static void forEachAutoImport(BiConsumer<String, String> action) throws JDOMException, IOException {
		Document preload = XmlUtilities.readDocFromFile(Application.getModuleDataFile(AUTO_IMPORT_FILENAME));

		@SuppressWarnings("unchecked")
		List<Element> preloadClasses = preload.getRootElement().getChildren("class");
		for (int i = 0; i < preloadClasses.size(); i++) {
			Element preloadClass = preloadClasses.get(i);
			String packageName = preloadClass.getChildText("package");
			String className = preloadClass.getChildText("name");
			action.accept(packageName, className);
		}
	}

	/**
	 * The name of this plugin instance.
	 */
	private String name;

	/**
	 * Creates a new DragonPlugin.
	 *
	 * @param tool The plugin tool that this plugin is added to.
	 *
	 * @param name The name of the language provided by the instance.
	 */
	public DragonPlugin(PluginTool tool, String name) {
		super(tool);
		this.name = name;

		// set up the preload option
		ToolOptions toolOpt = tool.getOptions(OPTION_CATEGORY_NAME);
		toolOpt.registerOption(getAutoImportOptionName(), Boolean.TRUE, getAutoImportOptionHelpLocation(),
				getAutoImportOptionDescription());
	}

	/**
	 * Gets the description of the automatic import option, customized with this
	 * instance's name.
	 *
	 * @return The option description.
	 *
	 * @since 2.2.0
	 */
	public String getAutoImportOptionDescription() {
		return "If set, loads a list of common Ghidra classes into the " + name
				+ "interpeter as soon as it is opened or reset.";
	}

	/**
	 * Gets the help location of the automatic import option for this instance.
	 *
	 * @return The option help location.
	 *
	 * @since 2.2.0
	 */
	public HelpLocation getAutoImportOptionHelpLocation() {
		return new HelpLocation(name, "Import_Classes_In_" + name + "_Interpreter");
	}

	/**
	 * Gets the name of the automatic import option, customized with this instance's
	 * name.
	 *
	 * @return The option name.
	 *
	 * @since 2.2.0
	 */
	public String getAutoImportOptionName() {
		return "Automatically import classes in " + name;
	}

	/**
	 * Get a list of completions for the given command prefix.
	 *
	 * @param cmd The command to try to complete.
	 *
	 * @return A list of possible code completions.
	 */
	@Override
	public List<CodeCompletion> getCompletions(String cmd) {
		return getInterpreter().getCompletions(cmd);
	}

	/**
	 * The icon for this plugin.
	 */
	@Override
	public ImageIcon getIcon() {
		String imageFilename = "images/" + name.toLowerCase() + ".png";
		return ResourceManager.loadImage(imageFilename);
	}

	/**
	 * Gives the interpreter currently in use by the plugin.
	 *
	 * @return The interpreter for this plugin.
	 */
	public abstract GhidraInterpreter getInterpreter();

	/**
	 * The title of the plugin.
	 */
	@Override
	public String getTitle() {
		return name;
	}

	/**
	 * Called whenever the highlight is changed within the CodeBrowser tool.
	 */
	@Override
	public void highlightChanged(ProgramSelection sel) {
		getInterpreter().updateHighlight(sel);
	}

	/**
	 * Whether or not automatic imports are currently enabled in this plugin.
	 *
	 * @return Whether or not automatic imports are enabled in this plugin.
	 *
	 * @since 2.2.0
	 */
	public boolean isAutoImportEnabled() {
		return tool.getOptions(DragonPlugin.OPTION_CATEGORY_NAME).getBoolean(getAutoImportOptionName(), false);
	}

	/**
	 * Called whenever the location is changed within the CodeBrowser tool.
	 */
	@Override
	public void locationChanged(ProgramLocation loc) {
		getInterpreter().updateLocation(loc);
	}

	/**
	 * Called whenever a program is activate within the CodeBrowser tool.
	 */
	@Override
	public void programActivated(Program program) {
		getInterpreter().updateProgram(program);
	}

	/**
	 * Called whenever the selection is changed within the CodeBrowser tool.
	 */
	@Override
	public void selectionChanged(ProgramSelection sel) {
		getInterpreter().updateSelection(sel);
	}

}
