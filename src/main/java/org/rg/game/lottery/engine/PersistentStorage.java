package org.rg.game.lottery.engine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
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
	String fileName;
	int size;

	public PersistentStorage(
		LocalDate extractionDate,
		int combinationCount,
		int numberOfCombos,
		String suffix
	) {
		buildWorkingPath();
		absolutePath = buildWorkingPath() + File.separator +
			(fileName = "[" + extractionDate.toString() + "]"+"[" + combinationCount +"]" +
			"[" + numberOfCombos + "]" + /*"[" + toRawString(numbers) + "]" +*/ suffix + ".txt");
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

	public static String buildWorkingPath() {
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
		File workingFolder = new File(workingPath);
		workingFolder.mkdirs();
		return workingPath;
	}

	public String getAbsolutePath() {
		return absolutePath;
	}

	public String getFileName() {
		return fileName;
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
	public void close() {
		try {
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
