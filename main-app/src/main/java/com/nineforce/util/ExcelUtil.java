package com.nineforce.util;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

import java.util.Iterator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExcelUtil {
    public static final Logger logger = LoggerFactory.getLogger(ExcelUtil.class);

    public ExcelUtil() {
    }

    public static int getCellIntValue(XSSFRow xssfRow, int index) throws NumberFormatException {
        XSSFCell cell = xssfRow.getCell(index, MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return 0;
        } else {
            int cellValue;
            switch (cell.getCellType()) {
                case NUMERIC:
                    cellValue = (int)cell.getNumericCellValue();
                    break;
                case STRING:
                    try {
                        cellValue = Integer.parseInt(cell.getStringCellValue());
                        break;
                    } catch (NumberFormatException var5) {
                        NumberFormatException e = var5;
                        logger.warn("Error in getCellIntValue. cell type = {}, cell value = {}", cell.getCellType(), cell.getStringCellValue());
                        throw e;
                    }
                default:
                    logger.error("Error in getCellIntValue. cell type = {}", cell.getCellType());
                    cellValue = 0;
            }

            return cellValue;
        }
    }

    public static double getCellDoubleValue(XSSFRow xssfRow, int index) throws NumberFormatException {
        XSSFCell cell = xssfRow.getCell(index, MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return 0.0;
        } else {
            double cellValue;
            switch (cell.getCellType()) {
                case NUMERIC:
                    cellValue = cell.getNumericCellValue();
                    break;
                case STRING:
                    try {
                        String str = cell.getStringCellValue();
                        if (str.equals("--")) {
                            return 0.0;
                        }

                        cellValue = Double.parseDouble(str);
                        break;
                    } catch (NumberFormatException var6) {
                        NumberFormatException e = var6;
                        logger.warn("Error in getCellIntValue. cell type = {}, cell value = {}", cell.getCellType(), cell.getStringCellValue());
                        throw e;
                    }
                default:
                    logger.error("Error in getCellDoubleVaule. cell type = {}", cell.getCellType());
                    cellValue = 0.0;
            }

            return cellValue;
        }
    }

    public static String getCellStringValue(XSSFRow xssfRow, int index) {
        XSSFCell cell = xssfRow.getCell(index, MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return "";
        } else {
            switch (cell.getCellType()) {
                case NUMERIC:
                    return String.valueOf(cell.getNumericCellValue());
                case STRING:
                    return cell.getStringCellValue();
                case BLANK:
                    return "";
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case ERROR:
                    return cell.getErrorCellValue() + "";
                case FORMULA:
                    Workbook workbook = cell.getSheet().getWorkbook();
                    FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
                    CellValue cellValue = evaluator.evaluate(cell);
                    switch (cellValue.getCellType()) {
                        case NUMERIC:
                            return String.valueOf(cellValue.getNumberValue());
                        case STRING:
                            return cellValue.getStringValue();
                        case BLANK:
                            return "";
                        case BOOLEAN:
                            return String.valueOf(cellValue.getBooleanValue());
                        case ERROR:
                            return cellValue.getErrorValue() + "";
                        default:
                            return "";
                    }
                default:
                    return "";
            }
        }
    }

    public static boolean getCellBooleanValue(XSSFRow xssfRow, int index) {
        Cell c = xssfRow.getCell(index);
        if (c == null) {
            return false;
        }

        switch (c.getCellType()) {
            case BOOLEAN:
                return c.getBooleanCellValue();

            case NUMERIC:
                // treat 0 as false, any other number as true
                return c.getNumericCellValue() != 0;

            case STRING:
                String s = c.getStringCellValue().trim().toLowerCase();
                // handle "true"/"false", "yes"/"no", "1"/"0"
                if ("true".equals(s) || "yes".equals(s) || "1".equals(s)) {
                    return true;
                }
                return false;

            default:
                return false;
        }
    }


    public static void setCellDoubleValue(XSSFRow xssfRow, int index, double value) {
        XSSFCell cell = xssfRow.getCell(index, MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            cell = xssfRow.createCell(index);
        }

        cell.setCellFormula((String)null);
        cell.setCellValue(value);
    }

    public static void setCellIntValue(XSSFRow xssfRow, int index, int value) {
        XSSFCell cell = xssfRow.getCell(index, MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            cell = xssfRow.createCell(index);
        }

        cell.setCellFormula((String)null);
        cell.setCellValue((double)value);
    }

    public static void setCellStringValue(XSSFRow xssfRow, int index, String value) {
        XSSFCell cell = xssfRow.getCell(index, MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            cell = xssfRow.createCell(index);
        }

        cell.setCellValue(value);
    }

    public static void copyRowStyle(Row sourceRow, Row destRow) {
        Iterator var2 = sourceRow.iterator();

        while(var2.hasNext()) {
            Cell sourceCell = (Cell)var2.next();
            Cell destCell = destRow.getCell(sourceCell.getColumnIndex());
            copyCellStyle(sourceCell, destCell);
        }

    }

    public static void copyCellStyle(Cell sourceCell, Cell destCell) {
        CellStyle sourceCellStyle = sourceCell.getCellStyle();
        CellStyle destCellStyle = destCell.getSheet().getWorkbook().createCellStyle();
        destCellStyle.cloneStyleFrom(sourceCellStyle);
        destCell.setCellStyle(destCellStyle);
    }

    public static void copyRowValue(Row sourceRow, Row destRow) {
        Iterator var2 = sourceRow.iterator();

        while(var2.hasNext()) {
            Cell sourceCell = (Cell)var2.next();
            Cell destCell = destRow.createCell(sourceCell.getColumnIndex());
            copyCell(sourceCell, destCell);
        }

    }

    public static void copyCell(Cell sourceCell, Cell destCell) {
        switch (sourceCell.getCellType()) {
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(sourceCell)) {
                    CellStyle sourceCellStyle = sourceCell.getCellStyle();
                    CellStyle destCellStyle = destCell.getSheet().getWorkbook().createCellStyle();
                    destCellStyle.cloneStyleFrom(sourceCellStyle);
                    destCell.setCellValue(sourceCell.getDateCellValue());
                    short sourceFormat = sourceCellStyle.getDataFormat();
                    destCellStyle.setDataFormat(sourceFormat);
                    destCell.setCellStyle(destCellStyle);
                } else {
                    destCell.setCellValue(sourceCell.getNumericCellValue());
                }
                break;
            case STRING:
                destCell.setCellValue(sourceCell.getStringCellValue());
            case BLANK:
            default:
                break;
            case BOOLEAN:
                destCell.setCellValue(sourceCell.getBooleanCellValue());
                break;
            case ERROR:
                destCell.setCellValue((double)sourceCell.getErrorCellValue());
                break;
            case FORMULA:
                destCell.setCellFormula(sourceCell.getCellFormula());
        }

    }
}