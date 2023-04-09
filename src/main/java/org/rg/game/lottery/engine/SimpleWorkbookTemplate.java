package org.rg.game.lottery.engine;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFFont;

public class SimpleWorkbookTemplate implements Closeable {
	Workbook workbook;
	CellStyle headerStyle;
	CellStyle contentStyle;
	CellStyle hyperLinkStyle;
	Map<String, CellStyle> formattedContentStyle;
	DataFormat dataFormat;
	Map<String, Integer> headersSize;
	boolean autosizeColumnFeatureEnabled;
	String currentSheet;
	Map<String, Integer> currentRow;
	Map<String, Integer> currentCol;

	public SimpleWorkbookTemplate() {
		this(false);
	}

	public SimpleWorkbookTemplate(boolean largeContent) {
		this.workbook = largeContent ? new SXSSFWorkbook() : new HSSFWorkbook();
		autosizeColumnFeatureEnabled = !largeContent;
		dataFormat =  workbook.createDataFormat();
		headersSize = new HashMap<>();
		headerStyle = createHeaderStyle(workbook);
		contentStyle = createContentStyle(workbook);
		hyperLinkStyle = createHyperLinkStyle(workbook);
		formattedContentStyle = new HashMap<>();
		currentRow = new HashMap<>();
		currentCol = new HashMap<>();
	}

	private int get(String sheetName, Map<String, Integer> map) {
		Integer value = map.get(sheetName);
		if (value == null) {
			 map.put(sheetName, 0);
			 return 0;
		}
		return value;
	}

	private int reset(String sheetName, Map<String, Integer> map) {
		map.put(sheetName, 0);
		return 0;
	}

	private int getAndIncrement(String sheetName, Map<String, Integer> map) {
		Integer value = map.get(sheetName);
		if (value == null) {
			 map.put(sheetName, 1);
			 return 0;
		}
		map.put(sheetName, value + 1);
		return value;
	}

	protected CellStyle createHeaderStyle(Workbook workbook) {
		return createStyle(workbook, cellStyle -> fontStyle -> {
			fontStyle.setBold(true);
			cellStyle.setAlignment(HorizontalAlignment.CENTER);
			cellStyle.setFillForegroundColor(getHeaderColor());
			cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			cellStyle.setVerticalAlignment(VerticalAlignment.TOP);
			cellStyle.setWrapText(true);
		});
	}

	protected short getHeaderColor() {
		return IndexedColors.LIGHT_GREEN.getIndex();
	}

	protected CellStyle createContentStyle(Workbook workbook) {
		return createStyle(workbook, cellStyle -> fontStyle -> {});
	}

	protected CellStyle createHyperLinkStyle(Workbook workbook) {
		return createStyle(workbook, cellStyle -> fontStyle -> {
			fontStyle.setColor(IndexedColors.BLUE.index);
			fontStyle.setUnderline(XSSFFont.U_SINGLE);
		});
	}

	protected String getDefaultNumberFormat() {
		return "#,##0.00";
	}

	protected String getDefaultDateFormat() {
		return "dd/MM/yyyy";
	}

	public CellStyle getOrCreateStyle(Object... objects) {
		String name = null;
		String pattern = null;
		for (int i = 0; i < objects.length; i++) {
			if ((i == 0 && objects[i] instanceof String) ||
				i > 0 && name == null && objects[i] instanceof String) {
				name = (String)objects[i];
				if (i > 0) {
					pattern = name;
				}
			}
		}
		CellStyle style = formattedContentStyle.get(name);
		if (style == null) {
			style = createStyle(
				workbook,
				null
			);
			formattedContentStyle.put(name, style);
		}
		for (int i = 0; i < objects.length; i++) {
			if (objects[i] instanceof CellStyle) {
				style.cloneStyleFrom((CellStyle)objects[i]);
			} else if (objects[i] instanceof HorizontalAlignment) {
				style.setAlignment((HorizontalAlignment)objects[i]);
			} else if (objects[i] instanceof FillPatternType) {
				style.setFillPattern((FillPatternType) objects[i]);
			} else if (objects[i] instanceof IndexedColors) {
				style.setFillForegroundColor(((IndexedColors) objects[i]).getIndex());
			}
		}
		if (pattern != null) {
			style.setDataFormat(dataFormat.getFormat(pattern));
		}
		return style;
	}

	public CellStyle getOrCreateStyle(Workbook workbook, String pattern) {
		CellStyle style = formattedContentStyle.get(pattern);
		if (style == null) {
			style = createStyle(workbook, cellStyle -> fontStyle -> {
				cellStyle.setDataFormat(dataFormat.getFormat(pattern));
			});
			formattedContentStyle.put(pattern, style);
		}
		return style;
	}

	public void disableAutosizeColumnFeature() {
		this.autosizeColumnFeatureEnabled = false;
	}

	public void enableAutosizeColumnFeature() {
		this.autosizeColumnFeatureEnabled = true;
	}

	public Sheet getOrCreateSheet(String name) {
		Sheet sheet = workbook.getSheet(name);
		if (sheet == null) {
			sheet = workbook.createSheet(name);
			if (autosizeColumnFeatureEnabled && sheet instanceof SXSSFSheet) {
				((SXSSFSheet)sheet).trackAllColumnsForAutoSizing();
			}
		}
		return sheet;
	}

	public Sheet getOrCreateSheet(String name, boolean currentSheet) {
		Sheet sheet = getOrCreateSheet(name);
		if (currentSheet) {
			setCurrentSheet(name);
		}
		return sheet;
	}

	public void setCurrentSheet(String name) {
		this.currentSheet = name;
	}

	public Row addRow() {
		Row row = getOrCreateRow(currentSheet, getAndIncrement(currentSheet, currentRow));
		reset(currentSheet, currentCol);
		return row;
	}

	private Row getOrCreateRow(String sheetName, int rowIdx) {
		Sheet sheet = getOrCreateSheet(sheetName);
		Row row = sheet.getRow(rowIdx);
		if (row == null) {
			row = sheet.createRow(rowIdx);
		}
		return row;
	}

	public Row createHeader(String... titles) {
		return createHeader(true, titles);
	}

	public Row createHeader(boolean blocked, String... titles) {
		return createHeader(currentSheet, blocked, Arrays.asList(titles));
	}

	public Row createHeader(boolean blocked, List<String> titles) {
		return createHeader(currentSheet, blocked, titles);
	}

	public Row createHeader(String sheetName, boolean blocked, List<String> titles) {
		int columnIndex = 0;
		for (String title : titles) {
			createHeaderCell(sheetName, 0, columnIndex++, title);
		}
		headersSize.put(sheetName, titles.size());
		if (blocked) {
			getOrCreateSheet(sheetName).createFreezePane(0, 1);
		}
		return getOrCreateRow(sheetName, 0);
	}

	public void addCell(String... value) {
		if (value == null) {
			value = new String[0];
		}
		for (int i = 0; i < value.length; i++) {
			createCell(currentSheet, get(currentSheet, currentRow), getAndIncrement(currentSheet, currentCol), value[i]);
		}
	}

	public void addCellOrEmptyCell(String... value) {
		if (value == null) {
			value = new String[0];
		}
		for (int i = 0; i < value.length; i++) {
			createCell(currentSheet, get(currentSheet, currentRow), getAndIncrement(currentSheet, currentCol), value[i] != null ? value[i] : "");
		}
	}

	public Cell createCell(int rowIdx, int colIdx, Number value) {
		return createCell(currentSheet, rowIdx, colIdx, value);
	}

	public Cell createCell(int rowIdx, int colIdx, Date value) {
		return createCell(currentSheet, rowIdx, colIdx, value);
	}

	public Cell createCell(int rowIdx, int colIdx, String value) {
		return createCell(currentSheet, rowIdx, colIdx, value);
	}

	public Cell addCell(Number value) {
		return addCell(value, getDefaultNumberFormat());
	}

	public Cell addCell(Number value, String format) {
		return createCell(currentSheet, get(currentSheet, currentRow), getAndIncrement(currentSheet, currentCol), value, format);
	}

	public Cell addCellOrZeroCell(Number value) {
		return addCellOrZeroCell(value, getDefaultNumberFormat());
	}

	public Cell addCellOrZeroCell(Number value, String format) {
		return addCell(Optional.ofNullable(value).orElseGet(() -> BigDecimal.ZERO), format);
	}

	public Cell createCell(String sheetName, int rowIdx, int colIdx, Number value) {
		return createCell(sheetName, rowIdx, colIdx, value, getDefaultNumberFormat());
	}

	public Cell createCell(String sheetName, int rowIdx, int colIdx, Number value, String format) {
		Row row = getOrCreateRow(sheetName, rowIdx);
		Cell cell = row.createCell(colIdx);
		cell.setCellValue(value.floatValue());
		cell.setCellStyle(getOrCreateStyle(workbook, format));
		return cell;
	}

	public Cell addCell(Date value) {
		return addCell(value, getDefaultDateFormat());
	}

	public Cell addFormulaCell(String value) {
		return createFormulaCell(currentSheet, get(currentSheet, currentRow), getAndIncrement(currentSheet, currentCol), value);
	}

	public Cell addFormulaCell(String value, String format) {
		return createFormulaCell(currentSheet, get(currentSheet, currentRow), getAndIncrement(currentSheet, currentCol), value, format);
	}

	public Cell addCell(Date value, String format) {
		return createCell(currentSheet, get(currentSheet, currentRow), getAndIncrement(currentSheet, currentCol), value, format);
	}

	public Cell createCell(String sheetName, int rowIdx, int colIdx, Date value) {
		return createCell(sheetName, rowIdx, colIdx, value, getDefaultDateFormat());
	}

	public Cell createCell(String sheetName, int rowIdx, int colIdx, Date value, String format) {
		Row row = getOrCreateRow(sheetName, rowIdx);
		Cell cell = row.createCell(colIdx);
		cell.setCellValue(value);
		cell.setCellStyle(getOrCreateStyle(workbook, format));
		return cell;
	}

	public Cell createCell(String sheetName, int rowIdx, int colIdx, String value) {
		Row row = getOrCreateRow(sheetName, rowIdx);
		Cell cell = row.createCell(colIdx);
		cell.setCellValue(value);
		cell.setCellStyle(contentStyle);
		return cell;
	}

	private Cell createFormulaCell(String sheetName, int rowIdx, int colIdx, String value, String format) {
		Row row = getOrCreateRow(currentSheet, rowIdx);
		Cell cell = row.createCell(colIdx);
		cell.setCellFormula(value);
		cell.setCellStyle(getOrCreateStyle(workbook, format));
		return cell;
	}

	public Cell createFormulaCell(String sheetName, int rowIdx, int colIdx, String value) {
		Row row = getOrCreateRow(sheetName, rowIdx);
		Cell cell = row.createCell(colIdx);
		cell.setCellFormula(value);
		cell.setCellStyle(contentStyle);
		return cell;
	}

	public Cell createHeaderCell(String sheetName, int rowIdx, int colIdx, String value) {
		Row row = getOrCreateRow(sheetName, rowIdx);
		Cell cell = row.createCell(colIdx);
		cell.setCellValue(value);
		cell.setCellStyle(headerStyle);
		return cell;
	}

	CellStyle createStyle(Workbook workbook, Function<CellStyle, Consumer<Font>> styleSetter) {
		CellStyle cellStyle = workbook.createCellStyle();
		Font fontStyle = workbook.createFont();
		cellStyle.setFont(fontStyle);
		if (styleSetter != null) {
			styleSetter.apply(cellStyle).accept(fontStyle);
		}
		return cellStyle;
	}

	public Workbook getWorkbook() {
		return this.workbook;
	}

	public void autosizeColumn() {
		if (autosizeColumnFeatureEnabled) {
			for (Map.Entry<String, Integer> entry : headersSize.entrySet()) {
				int columnIndex = entry.getValue();
				Sheet sheet = getOrCreateSheet(entry.getKey());
				for (int i = 0; i < columnIndex; i++) {
					sheet.autoSizeColumn(i);
				}
			}
		}
	}

	public void setLinkForCell(HyperlinkType type, Cell cell, String address) {
		Hyperlink hyperLink = getCreationHelper().createHyperlink(type);
		hyperLink.setAddress(address);
		cell.setHyperlink(hyperLink);
		cell.setCellStyle(hyperLinkStyle);
	}

	public CreationHelper getCreationHelper() {
		if (workbook instanceof SXSSFWorkbook) {
			return ((SXSSFWorkbook)workbook).getCreationHelper();
		} else if (workbook instanceof HSSFWorkbook) {
			return ((HSSFWorkbook)workbook).getCreationHelper();
		}
		return null;
	}

	@Override
	public void close() throws IOException {
		if (workbook != null) {
			workbook.close();
		}
	}

	public void setAutoFilter() {
		workbook.getSheet(currentSheet).setAutoFilter(
			new CellRangeAddress(
				0, currentRow.size() - 1, 0, headersSize.get(currentSheet) - 1
			)
		);
	}

}