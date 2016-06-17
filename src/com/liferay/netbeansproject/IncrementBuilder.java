/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.netbeansproject;

import com.liferay.netbeansproject.container.JarDependency;
import com.liferay.netbeansproject.container.Module;
import com.liferay.netbeansproject.util.FileUtil;
import com.liferay.netbeansproject.util.GradleUtil;
import com.liferay.netbeansproject.util.ModuleUtil;
import com.liferay.netbeansproject.util.PropertiesUtil;
import com.liferay.netbeansproject.util.StringUtil;

import java.io.IOException;

import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author Tom Wang
 */
public class IncrementBuilder {

	public static void main(String[] args) throws Exception {
		Properties buildProperties = PropertiesUtil.loadProperties(
			Paths.get("build.properties"));

		Path projectDirPath = Paths.get(
			PropertiesUtil.getRequiredProperty(buildProperties, "project.dir"));

		IncrementBuilder incrementBuilder = new IncrementBuilder();

		ProjectBuilder projectBuilder = new ProjectBuilder();

		for (String portalDir : StringUtil.split(
				PropertiesUtil.getRequiredProperty(
					buildProperties, "portal.dirs"),
				',')) {

			Path portalDirPath = Paths.get(portalDir);

			Path portalProjectPath = projectDirPath.resolve(
				portalDirPath.getFileName());

			if (Files.exists(portalProjectPath)) {
				incrementBuilder.addModule(
					portalProjectPath, portalDirPath, buildProperties);
			}
			else {
				projectBuilder.scanPortal(
					portalProjectPath, portalDirPath, buildProperties);
			}
		}
	}

	public void addModule(
			final Path projectRootPath, final Path portalPath,
			Properties buildProperties)
		throws IOException {

		final Map<Path, Module> existProjectMap = _getExistingProjects(
			projectRootPath);

		final String ignoredDirs = buildProperties.getProperty("ignored.dirs");

		final boolean displayGradleProcessOutput = Boolean.valueOf(
			buildProperties.getProperty("display.gradle.process.output"));

		final Properties projectDependencyProperties =
			PropertiesUtil.loadProperties(
				Paths.get("project-dependency.properties"));

		final String excludedTypes = buildProperties.getProperty(
			"exclude.types");

		final String portalLibJars = ModuleUtil.getPortalLibJars(portalPath);

		Files.walkFileTree(
			portalPath, EnumSet.allOf(FileVisitOption.class), Integer.MAX_VALUE,
			new SimpleFileVisitor<Path>() {

				@Override
				public FileVisitResult preVisitDirectory(
						Path path, BasicFileAttributes basicFileAttributes)
					throws IOException {

					Path pathFileName = path.getFileName();

					if (ignoredDirs.contains(pathFileName.toString())) {
						return FileVisitResult.SKIP_SUBTREE;
					}

					if (!Files.exists(path.resolve("src"))) {
						return FileVisitResult.CONTINUE;
					}

					try {
						Module existModule = existProjectMap.get(path);

						if ((existModule != null) &&
							existModule.equals(
								Module.createModule(
									null, path, null,
									projectDependencyProperties))) {

							return FileVisitResult.SKIP_SUBTREE;
						}

						String moduleName = ModuleUtil.getModuleName(path);

						Path moduleProjectPath = projectRootPath.resolve(
							Paths.get("modules", moduleName));

						FileUtil.delete(moduleProjectPath);

						Map<String, List<JarDependency>> jarDependenciesMap =
							new HashMap<>();

						if (Files.exists(path.resolve("build.gradle"))) {
							jarDependenciesMap = GradleUtil.getJarDependencies(
								portalPath, path, displayGradleProcessOutput);
						}

						FileUtil.unZip(
							projectRootPath.resolve(
								Paths.get("modules", moduleName)));

						Module module = Module.createModule(
							projectRootPath.resolve(
								Paths.get(
									"modules", ModuleUtil.getModuleName(path))),
							path, jarDependenciesMap.get(moduleName),
							projectDependencyProperties);

						CreateModule.createModule(
							module, projectRootPath, excludedTypes,
							portalLibJars, portalPath);
					}
					catch (IOException ioe) {
						throw ioe;
					}
					catch (Exception e) {
						throw new IOException(e);
					}

					return FileVisitResult.SKIP_SUBTREE;
				}

			});
	}

	private Map<Path, Module> _getExistingProjects(Path projectRootPath)
		throws IOException {

		Map<Path, Module> map = new HashMap<>();

		for (Path path :
				Files.newDirectoryStream(projectRootPath.resolve("modules"))) {

			Module module = Module.load(path);

			if (module != null) {
				map.put(module.getModulePath(), module);
			}
		}

		return map;
	}

}