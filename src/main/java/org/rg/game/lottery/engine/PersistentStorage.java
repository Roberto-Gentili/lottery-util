package org.rg.game.lottery.engine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PersistentStorage implements Storage {
	private static String workingPath;
	BufferedWriter bufferedWriter = null;
	String absolutePath;
	String parentPath;
	String name;
	int size;
	Boolean isClosed;

	public PersistentStorage(
		LocalDate extractionDate,
		int combinationCount,
		int numberOfCombos,
		String group,
		String suffix
	) {
		absolutePath = (parentPath = buildWorkingPath(group)) + File.separator +
			(name = Storage.computeName(extractionDate, combinationCount, numberOfCombos, suffix));
		try (FileChannel outChan = new FileOutputStream(absolutePath, true).getChannel()) {
		  outChan.truncate(0);
		} catch (IOException exc) {
			//exc.printStackTrace();
		}
		try {
			bufferedWriter = new BufferedWriter(new FileWriter(absolutePath, false));
		} catch (IOException exc) {
			throw new RuntimeException(exc);
		}
	}

	private PersistentStorage(String group, String fileName) {
		absolutePath = (parentPath = buildWorkingPath(group)) + File.separator + fileName;
		name = fileName;
	}

	public static PersistentStorage restore(String group, String fileName) {
		PersistentStorage storage = new PersistentStorage(group, fileName) {
			@Override
			public boolean addCombo(List<Integer> selectedCombo) {
				throw new UnsupportedOperationException(this + " is only readable");
			}
			@Override
			public boolean addLine() {
				throw new UnsupportedOperationException(this + " is only readable");
			}
			@Override
			public boolean addLine(String value) {
				throw new UnsupportedOperationException(this + " is only readable");
			}
			@Override
			public void addUnindexedCombo(List<Integer> selectedCombo) {
				throw new UnsupportedOperationException(this + " is only readable");
			}
		};
		Iterator<List<Integer>> comboIterator = storage.iterator();
		try {
			while (comboIterator.hasNext()) {
				comboIterator.next();
				storage.size++;
			}
		} catch (RuntimeException exc) {
			if (!(exc.getCause() instanceof FileNotFoundException)) {
				throw exc;
			}
			return null;
		}
		return storage;
	}

	public static String buildWorkingPath() {
		return buildWorkingPath(null);
	}

	public static String buildWorkingPath(String subFolder) {
		if (workingPath == null) {
			synchronized (PersistentStorage.class) {
				if (workingPath == null) {
					String workingPath = System.getenv("lottery-util.working-path");
					workingPath =
						workingPath != null ? workingPath :
						System.getProperty("user.home") + File.separator +
						"Desktop" + File.separator +
						"Combos";
					System.out.println("Set working path to: " + workingPath);
					PersistentStorage.workingPath = workingPath;
				}
			}
		}
		String absolutePath = workingPath + (subFolder != null? File.separator + subFolder : "");
		File workingFolder = new File(absolutePath);
		workingFolder.mkdirs();
		return absolutePath;
	}

	public String getAbsolutePath() {
		return absolutePath;
	}

	public String getAbsolutePathWithoutExtension() {
		return absolutePath.substring(0, absolutePath.lastIndexOf("."));
	}

	@Override
	public String getName() {
		return name;
	}

	public String getParentPath() {
		return name;
	}


	public String getNameWithoutExtension() {
		return name.substring(0, name.lastIndexOf("."));
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public List<Integer> getCombo(int idx) {
		try (BufferedReader bufferedReader = new BufferedReader(new FileReader(absolutePath))){
			String line = null;
			int iterationIndex = 0;
			while ((line = bufferedReader.readLine()) != null) {
				if (line.split("\\t").length > 0) {
					if (iterationIndex == idx) {
						List<Integer> selectedCombo = new ArrayList<>();
						for (String numberAsString : line.split("\\t")) {
							try {
								selectedCombo.add(Integer.parseInt(numberAsString));
							} catch (NumberFormatException exc) {
								return null;
							}
						}
						return selectedCombo;
					}
					iterationIndex++;
				}
			}
		} catch (IOException exc) {
			throw new RuntimeException(exc);
		}
		return null;
	}

	@Override
	public Iterator<List<Integer>> iterator() {
		return new Iterator<List<Integer>>() {
			int currentIndex = 0;
			@Override
			public List<Integer> next() {
				return getCombo(currentIndex++);
			}

			@Override
			public boolean hasNext() {
				return getCombo(currentIndex) != null;
			}
		};
	}

	@Override
	public boolean addCombo(List<Integer> selectedCombo) {
		if (!contains(selectedCombo)) {
			try {
				bufferedWriter.write(ComboHandler.toString(selectedCombo) + "\n");
				bufferedWriter.flush();
				++size;
			} catch (IOException exc) {
				throw new RuntimeException(exc);
			}
			return true;
		}
		return false;
	}

	@Override
	public void addUnindexedCombo(List<Integer> selectedCombo) {
		try {
			bufferedWriter.write("\n" + ComboHandler.toString(selectedCombo));
			bufferedWriter.flush();
		} catch (IOException exc) {
			throw new RuntimeException(exc);
		}
	}

	public boolean contains(List<Integer> selectedCombo) {
		try (BufferedReader bufferedReader = new BufferedReader(new FileReader(absolutePath))){
			String line = null;
			while ((line = bufferedReader.readLine()) != null) {
				for (String numberAsString : line.split("\\t")) {
					if (!selectedCombo.contains(Integer.parseInt(numberAsString))) {
						return false;
					}
				}
				return true;
			}
		} catch (IOException exc) {
			throw new RuntimeException(exc);
		}
		return false;
	}

	@Override
	public void printAll() {
	    try (BufferedReader br = new BufferedReader(new FileReader(absolutePath))) {
	        String line;
	        while ((line = br.readLine()) != null) {
	           System.out.println(line);
	        }
	    } catch (IOException e) {
			e.printStackTrace();
		}
	 }

	@Override
	public boolean isClosed() {
		if (isClosed != null) {
			return isClosed;
		}
		if (bufferedWriter != null) {
			return false;
		}
		synchronized (this) {
			if (isClosed == null) {
				try (BufferedReader br = new BufferedReader(new FileReader(absolutePath))) {
			        String line;
			        while ((line = br.readLine()) != null) {
			           if (line.contains(END_LINE_PREFIX)) {
			        	   isClosed = true;
			           }
			        }
			    } catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
		return isClosed != null ? isClosed : false;
	}

	@Override
	public void close() {
		try {
			isClosed = true;
			bufferedWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean addLine(String value) {
		try {
			bufferedWriter.write("\n" + value);
			bufferedWriter.flush();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return true;
	}

	@Override
	public boolean addLine() {
		try {
			bufferedWriter.write("\n");
			bufferedWriter.flush();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return true;
	}

	@Override
	public void delete() {
		try {
			bufferedWriter.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		new File(absolutePath).delete();
	}

}
