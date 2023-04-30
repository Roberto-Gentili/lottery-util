package org.rg.game.lottery.engine;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class IOUtils {
	public static final IOUtils INSTANCE = new IOUtils();


	public void copy(InputStream input, OutputStream output) {
		try {
			byte[] buffer = new byte[1024];
			int bytesRead = 0;
			while (-1 != (bytesRead = input.read(buffer))) {
				output.write(buffer, 0, bytesRead);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public String getExtension(File file) {
		return file.getName().substring(file.getName().lastIndexOf(".") + 1, file.getName().length());
	}

	public List<File> findResources(FilenameFilter filter) {
		try {
			File resourceFolder = Paths.get(IOUtils.class.getResource("/" +
				IOUtils.class.getName().replace(".", "/") + ".class"
			).toURI()).toFile()
			.getParentFile().getParentFile().getParentFile()
			.getParentFile().getParentFile().getParentFile();
			return Arrays.asList(resourceFolder.listFiles(filter));
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

}
