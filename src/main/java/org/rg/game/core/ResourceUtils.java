package org.rg.game.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

public class ResourceUtils {
	public static final ResourceUtils INSTANCE = new ResourceUtils();

	public String getExtension(File file) {
		return file.getName().substring(file.getName().lastIndexOf(".") + 1, file.getName().length());
	}

	public List<File> find(FilenameFilter filter) {
		try {
			File resourceFolder = Paths.get(IOUtils.class.getResource("/" +
				IOUtils.class.getName().replace(".", "/") + ".class"
			).toURI()).toFile();
			for (String pathSegment : IOUtils.class.getName().split("\\.")) {
				resourceFolder = resourceFolder.getParentFile();
			}
			return toOrderedList(resourceFolder.listFiles(filter));
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	public List<File> findOrdered(String filePrefix, String extension, String... paths) {
		return toOrderedList(find(filePrefix, extension, paths), false);
	}

	public List<File> findReverseOrdered(String filePrefix, String extension, String... paths) {
		return toOrderedList(find(filePrefix, extension, paths), true);
	}

	public List<File> find(String filePrefix, String extension, String... paths) {
		Collection<File> files = new TreeSet<>((fISOne, fISTwo) -> {
			return fISOne.getName().compareTo(fISTwo.getName());
		});

		for (String path : paths) {
			files.addAll(
				Arrays.asList(
					new File(path).listFiles((directory, fileName) ->
						fileName.contains(filePrefix) && fileName.endsWith(extension)
					)
				)
			);
		}

		files.addAll(
			ResourceUtils.INSTANCE.find((directory, fileName) ->
				fileName.contains(filePrefix) && fileName.endsWith(extension)
			)
		);
		return toOrderedList(files);
	}


	public List<Properties> toOrderedProperties(List<File> files) throws IOException {
		List<Properties> properties = new ArrayList<>();
		for (int i = 0; i < files.size(); i++) {
			File file = files.get(i);
			try (InputStream configIS = new FileInputStream(file)) {
				Properties config = new Properties() {
					@Override
					public String toString() {
						String priority = getProperty("priority-of-this-configuration");
						if (priority != null) {
							return getProperty("file.name") + " - priority: " + priority;
						}
						return super.toString();
					}
				};
				config.setProperty("priority-of-this-configuration", String.valueOf((files.size() - 1) - i));
				config.load(configIS);
				config.setProperty("file.name", file.getName());
				config.setProperty("file.parent.absolutePath", file.getParentFile().getAbsolutePath());
				config.setProperty("file.extension", ResourceUtils.INSTANCE.getExtension(file));
				properties.add(config);
			}
		}
		Comparator<Properties> comparator = (propsOne, propsTwo) -> {
			BigDecimal propsOnePriority =
					MathUtils.INSTANCE.stringToBigDecimal(propsOne.getProperty("priority-of-this-configuration"));
				BigDecimal propsTwoPriority =
					MathUtils.INSTANCE.stringToBigDecimal(propsTwo.getProperty("priority-of-this-configuration"));
				return propsOnePriority.compareTo(propsTwoPriority);
		};
		Collections.sort(properties, comparator);
		return properties;
	}

	public File backup(
		File mainFile,
		String destFolderAbsolutePath
	) {
		return backup(LocalDateTime.now(), mainFile, destFolderAbsolutePath);
	}

	public File backup(
		LocalDateTime backupTime,
		File mainFile,
		String destFolderAbsolutePath
	) {
		try {
			String backupFileName = mainFile.getName().replace("." +  getExtension(mainFile), "") + " - [" + TimeUtils.dateTimeFormatForBackup.format(backupTime) + "]." + getExtension(mainFile);
			String backupFilePath = destFolderAbsolutePath + File.separator + backupFileName;
			if (!new File(backupFilePath).exists()) {
				try (InputStream inputStream = new FileInputStream(mainFile); OutputStream backupOutputStream = new FileOutputStream(backupFilePath)) {
					IOUtils.INSTANCE.copy(
						inputStream,
						backupOutputStream
					);
				}
				return new File(backupFilePath);
			}
			return null;
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public List<File> toOrderedList(Object files, boolean reversed) {
		Comparator<File> fileComparator = (fileOne, fileTwo) ->
			fileOne.getName().compareTo(fileTwo.getName());
		if (reversed) {
			fileComparator = fileComparator.reversed();
		}
		Set<File> orderedFiles = new TreeSet<>(fileComparator);
		if (files.getClass().isArray()) {
			orderedFiles.addAll(Arrays.asList((File[])files));
		} else if (files instanceof Collection) {
			orderedFiles.addAll((Collection<File>)files);
		}
		return new ArrayList<>(orderedFiles);
	}

	public List<File> toOrderedList(Object files) {
		return toOrderedList(files, false);
	}

}
