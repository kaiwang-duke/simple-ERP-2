package com.nineforce.service.tool;

import com.nineforce.util.ExcelUtil;
import com.nineforce.util.LocationComparator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.springframework.web.multipart.MultipartFile;

/**
 * It used to be WarehouseLocationConverter. Changed name and cloned the file to
 * the simple ERP project for all web based.
 */

@Service
public class InventoryFileConverterService {

    static final String FILE_NAME = "/Users/kaiwang/Downloads/product-V47.xlsx";
    static final boolean DO_WIRTE = true;

    final public static Logger logger = LoggerFactory.getLogger(InventoryFileConverterService.class);

    final static int PRD_SHEET_ACCT_INDEX = 0;
    final static int PRD_SHEET_SKU_INDEX = 1;
    final static int PRD_SHEET_FNSKU_INDEX = 2;
    final static int PRD_SHEET_DESC_INDEX = 8;

    // out sheet cell index makes package private because it is used in test
    static final int OUT_CELL_SKU_INDEX = 0;
    static final int OUT_CELL_DESC_INDEX = 1;
    static final int OUT_CELL_FNSKU_INDEX = 2;
    static final int OUT_CELL_TOTAL_INDEX = 3;
    static final int OUT_CELL_LOC_DETAIL_INDEX = 4;

    String inStrFile;
    String outStrFile;
    FileInputStream fileReadIn;
    XSSFWorkbook inWorkBook;
    FormulaEvaluator evaluator;
    HashMap<String, String[]> prdBaseMap;

    // sheets in the workbook. Must be correct
    final static String ACCT_PRD_SHEET = "product";
    final static String SKU_LOC_SHEET = "SKU-LOC";
    private final static String[] WAREHOUSE_SHEETS =
            {
                    "49",
                    "51",
                    "51B",
                    "52",
                    "MO-1",
                    "MO-2",
                    "ShiYan",
                    "QingXi",
                    "Transfer"
            };

    public InventoryFileConverterService(String strFile) {
        initFile(strFile);
    }

    public InventoryFileConverterService() {
    }

    void initFile(String strFile) {
        this.inStrFile = strFile;
        this.outStrFile = strFile.replace(".xlsx", "-out.xlsx");
        logger.info("Input file: " + inStrFile + " Output file: " + outStrFile);
        try {
            fileReadIn = new FileInputStream(inStrFile);
            inWorkBook = new XSSFWorkbook(fileReadIn);
            evaluator = inWorkBook.getCreationHelper().createFormulaEvaluator();
        } catch (Exception e) {
            logger.error("Error in initFile", e);
        }
    }


    /**
     * Convert MultipartFile to File
     * @param multipartFile
     * @return
     * @throws IOException
     */
    private File convertMultipartFileToFile(MultipartFile multipartFile) throws IOException {
        // Extract the original filename
        String originalFilename = multipartFile.getOriginalFilename();
        if (originalFilename == null) {
            throw new IOException("The original filename is null");
        }

        // take only the name, neo file extension
        int index = originalFilename.lastIndexOf(".");
        if (index != -1) {
            originalFilename = originalFilename.substring(0, index);
        }
        // Sanitize the original filename to remove any path separators
        originalFilename = originalFilename.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");

        // Create a unique temporary file in the /tmp directory with the sanitized original filename as a prefix and .xlsx as the suffix
        Path tempFilePath = Files.createTempFile(Paths.get("/tmp"), originalFilename + "-", ".xlsx");
        File tempFile = tempFilePath.toFile();

        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(multipartFile.getBytes());
        }

        return tempFile;
    }







    private void initFile(MultipartFile multipartFile) throws Exception {
        File file = convertMultipartFileToFile(multipartFile);
        initFile(file.getAbsolutePath());
    }


    /**
     * Get data from account-product sheet and store in a sorted map as
     * key: SKU and value: [FNSKU, Desc, Account]
     * In this sheet, account - 0, sku -1, fnsku - 2, desc - 8
     */
    HashMap<String, String[]> getProductBaseInfo() {
        HashMap<String, String[]> prdBaseMap = new HashMap<>();
        XSSFSheet sheet = inWorkBook.getSheet(ACCT_PRD_SHEET);
        for (Row row : sheet) {
            XSSFRow xssfRow = (XSSFRow) row;
            String sku = com.nineforce.util.ExcelUtil.getCellStringValue(xssfRow, PRD_SHEET_SKU_INDEX);
            String fnsku = ExcelUtil.getCellStringValue(xssfRow, PRD_SHEET_FNSKU_INDEX);
            String desc = ExcelUtil.getCellStringValue(xssfRow, PRD_SHEET_DESC_INDEX);
            String account = ExcelUtil.getCellStringValue(xssfRow, PRD_SHEET_ACCT_INDEX);

            String[] strArray = {fnsku, desc, account};
            prdBaseMap.put(sku, strArray);
        }
        return prdBaseMap;
    }


    public String createOrUpdateSkuLocSheet() {
        logger.info("Start parsing file {}", inStrFile);
        try {
            prdBaseMap = getProductBaseInfo();
            SortedMap<String, SkuLoc> mapSkuLoc =  readInSkuLocMap();

            writeOutSkuLocMap(mapSkuLoc);
            // Close the input file
            fileReadIn.close();
            // Notify the user that the operation has completed
        } catch (Exception e) {
            logger.error("Error in createOrUpdateSkuLocSheet. inStrFile = {}", inStrFile);
            e.printStackTrace();
        }
        return outStrFile;
    }

    public String createOrUpdateSkuLocSheet(MultipartFile strFile) throws Exception {
        initFile(strFile);
        String outputFilePath = createOrUpdateSkuLocSheet(); // Call the existing method to create/update
        logger.info("Output file created at: " + outputFilePath); // Log the output file path
        return outputFilePath;
    }


    SortedMap<String, SkuLoc> readInSkuLocMap() {

        // Create a map to store the concatenated values
        SortedMap<String, SkuLoc> mapSkuLoc = new TreeMap<>();

        for (String sheetName : WAREHOUSE_SHEETS) {
            // Get the first sheet from the workbook
            XSSFSheet sheet = inWorkBook.getSheet(sheetName);
            if (sheet == null) {
                logger.error("Sheet {} is not found in the workbook", sheetName);
                continue;
            }
            readInSkuLocMapPerSheet(mapSkuLoc, sheet);
        }  //end of for loop, for each sheet

        return mapSkuLoc;
    }

    /**
     * Read in Loc:unit-box:box-count from the sheet and store in the mapSkuLoc of format
     *  [sku, SkuLoc]
     * @param mapSkuLoc
     * @param sheet
     */
    private void   readInSkuLocMapPerSheet(SortedMap<String, SkuLoc> mapSkuLoc, XSSFSheet sheet) {

        final int LOC_INDEX_INPUT = 0;
        final int FNSKU_INDEX_INPUT = 1;
        final int UNIT_BOX_INDEX_INPUT = 2;
        final int NUM_OF_BOX_INDEX_INPUT = 3;
        //final int TOTAL_UNIT_INDEX = 4;  don't need this column
        final int SKU_INDEX_INPUT = 5;
        final int DESC_INDEX_INPUT = 6;
        final int COMMENT_INDEX_INPUT = 10;
        // only non-null row will be in the loop.

        logger.info("Start parsing sheet {}", sheet.getSheetName());

        int totalRows = sheet.getLastRowNum() - sheet.getFirstRowNum() + 1;

        for (int rowCount = 1; rowCount < totalRows; rowCount++) {
            int displayRow = rowCount + 1;
            XSSFRow xssfRow = sheet.getRow(rowCount);

            try {  // try catch here, so easy to log the row info
                String fnskuStr_lastFour = ExcelUtil.getCellStringValue(xssfRow, FNSKU_INDEX_INPUT);
                if (fnskuStr_lastFour.isEmpty()) {
                    logger.debug("FNSKU is empty(normal). row = {}", displayRow);
                    continue;
                }

                int unitBoxCount = ExcelUtil.getCellIntValue(xssfRow, UNIT_BOX_INDEX_INPUT);
                int numBoxCount = ExcelUtil.getCellIntValue(xssfRow, NUM_OF_BOX_INDEX_INPUT);
                if (unitBoxCount * numBoxCount == 0) {
                    logger.debug("Unit per Box and/or Box Count is ZERO(normal). row = {}", displayRow);
                    continue;
                }

                String strLoc = ExcelUtil.getCellStringValue(xssfRow, LOC_INDEX_INPUT);
                if (strLoc.isEmpty()) {
                    logger.debug("Location is empty. row = {}", displayRow);
                    continue;
                }

                // Create SKU-Locations sheet
                String sku = ExcelUtil.getCellStringValue(xssfRow, SKU_INDEX_INPUT);
                String desc = ExcelUtil.getCellStringValue(xssfRow, DESC_INDEX_INPUT);

                // need a function to get the FNSKU from the SKU
                if (sku.isEmpty()) {
                    logger.error("SKU is empty, but has FNSKU:{}, DESC: {}, row = {}, sheet = {}",
                            fnskuStr_lastFour, desc, displayRow, sheet.getSheetName());
                    continue;
                } else if (!prdBaseMap.containsKey(sku)) {

                    logger.error("SKU, {}, is not in the product base info sheet. row = {}", sku, displayRow);
                    continue;
                }
                String fnSku = prdBaseMap.get(sku)[0];

                String comment = ExcelUtil.getCellStringValue(xssfRow, COMMENT_INDEX_INPUT);

                // If the mapSkuLoc already contains the sku,concat the value, add total, and update the mapSkuLoc
                SkuLoc skuLoc = mapSkuLoc.get(sku);
                if (skuLoc == null) {
                    skuLoc = new SkuLoc(sku, desc, fnSku);
                }
                skuLoc.addLocation(strLoc, unitBoxCount, numBoxCount, comment);
                mapSkuLoc.put(sku, skuLoc);
            } catch (Exception e) {
                // row number start from 1
                logger.error("Error in readInSkuLocMapPerSheet. sheet {} and row = {}",
                        sheet.getSheetName(), displayRow);
                logger.debug(e.toString());
                e.printStackTrace();  // how much log at this time
            }
        }  //end of for loop, for each row
        logger.info("End parsing sheet {}", sheet.getSheetName());
    }

    void setUpSkuLocSheet(XSSFSheet outputSheetSkuLoc) {
        // Set the width of the column
        final int PIXEL_WIDTH = 30; // in characters
        final int DEFAULT_WIDTH = PIXEL_WIDTH * 157;
        final int TOTAL_WIDTH = PIXEL_WIDTH * 57;
        final int LOC_DETAIL_WIDTH = PIXEL_WIDTH * 236;

        outputSheetSkuLoc.setColumnWidth(OUT_CELL_SKU_INDEX, DEFAULT_WIDTH);
        outputSheetSkuLoc.setColumnWidth(OUT_CELL_DESC_INDEX, DEFAULT_WIDTH);
        outputSheetSkuLoc.setColumnWidth(OUT_CELL_FNSKU_INDEX, DEFAULT_WIDTH);
        outputSheetSkuLoc.setColumnWidth(OUT_CELL_TOTAL_INDEX, TOTAL_WIDTH);
        outputSheetSkuLoc.setColumnWidth(OUT_CELL_LOC_DETAIL_INDEX, LOC_DETAIL_WIDTH);

        // Write the header
        XSSFRow row = outputSheetSkuLoc.createRow(0);
        Cell skuCell = row.createCell(OUT_CELL_SKU_INDEX);
        skuCell.setCellValue("SKU");
        Cell descCell = row.createCell(OUT_CELL_DESC_INDEX);
        descCell.setCellValue("Description");
        Cell fnSkuCell = row.createCell(OUT_CELL_FNSKU_INDEX);
        fnSkuCell.setCellValue("FNSKU");
        Cell totalCell = row.createCell(OUT_CELL_TOTAL_INDEX);
        totalCell.setCellValue("Total");
        Cell locDetailCell = row.createCell(OUT_CELL_LOC_DETAIL_INDEX);
        locDetailCell.setCellValue("Location Detail");
    }

    /**
     * Write the mapSkuLoc to the output sheet, or overwrite.
     * SKU sorted and location is sorted, along with other info.
     *
     * @param mapSkuLoc the map to write out
     * @throws Exception that may occur:w
     *
     */
    void writeOutSkuLocMap(SortedMap<String, SkuLoc> mapSkuLoc) throws Exception {
        // Create a new sheet for the output. No XSSFWorkbook(outputStream) API. Use below.
        //FileOutputStream outputStream = new FileOutputStream(outStrFile);
        //XSSFWorkbook outBook = new XSSFWorkbook(outputStream);

        XSSFSheet outputSheetSkuLoc = inWorkBook.getSheet(SKU_LOC_SHEET);
        if (outputSheetSkuLoc != null) {
            inWorkBook.removeSheetAt(inWorkBook.getSheetIndex(outputSheetSkuLoc));
        }
        outputSheetSkuLoc = inWorkBook.createSheet(SKU_LOC_SHEET);

        setUpSkuLocSheet(outputSheetSkuLoc);

        // Write the map to the output sheet, or overwrite.
        // Set the style of the cell, wrap location detail and comment cell.
        CellStyle wrapStyle = inWorkBook.createCellStyle();
        wrapStyle.setWrapText(true);

        DataFormat format = inWorkBook.createDataFormat();
        CellStyle intStyle = inWorkBook.createCellStyle();
        intStyle.setDataFormat(format.getFormat("#,##0"));

        // Write SKU, Description, FNSKU, Total, Location Detail line by line
        int rowNum = 1;
        for (Map.Entry<String, SkuLoc> entry : mapSkuLoc.entrySet()) {
            SkuLoc skuLoc = entry.getValue();
            XSSFRow row = outputSheetSkuLoc.createRow(rowNum++);

            Cell skuCell = row.createCell(OUT_CELL_SKU_INDEX);
            skuCell.setCellValue(entry.getKey());
            skuCell.setCellStyle(wrapStyle);

            Cell descCell = row.createCell(OUT_CELL_DESC_INDEX);
            descCell.setCellValue(skuLoc.desc);
            descCell.setCellStyle(wrapStyle);

            Cell fnSkuCell = row.createCell(OUT_CELL_FNSKU_INDEX);
            fnSkuCell.setCellValue(skuLoc.fnSku);
            fnSkuCell.setCellStyle(wrapStyle);

            Cell totalCell = row.createCell(OUT_CELL_TOTAL_INDEX);
            totalCell.setCellValue(skuLoc.totalUnitCount);
            totalCell.setCellStyle(intStyle);

            Cell locCountCell = row.createCell(OUT_CELL_LOC_DETAIL_INDEX);
            locCountCell.setCellValue(skuLoc.getLocationDetail());
            locCountCell.setCellStyle(wrapStyle);
        }
        // Write the workbook back to the input file
        FileOutputStream fileOut = new FileOutputStream(outStrFile);
        inWorkBook.setSheetOrder(outputSheetSkuLoc.getSheetName(), 0);
        inWorkBook.write(fileOut);
        fileOut.close();
    }

    /**
     * Red the -out file and return a map of SKU and SkuLoc
     * @return Map of <string, string></string,>
     */
    Map<String, String> getSkuLoc() {
        Map<String,String> skuLocMap = new HashMap<>();
        // open the -out file
        try {
            fileReadIn = new FileInputStream(outStrFile);
            inWorkBook = new XSSFWorkbook(fileReadIn);
            XSSFSheet sheet = inWorkBook.getSheet(SKU_LOC_SHEET);

            for (Row row : sheet) {
                XSSFRow xssfRow = (XSSFRow) row;
                String sku = com.nineforce.util.ExcelUtil.getCellStringValue(xssfRow, OUT_CELL_SKU_INDEX);
                String locDetail = com.nineforce.util.ExcelUtil.getCellStringValue(xssfRow, OUT_CELL_LOC_DETAIL_INDEX);
                skuLocMap.put(sku, locDetail);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
        return skuLocMap;
    }

    /**
     * inner class for accumulated location, count, and comment
     */
    static class SkuLoc {
        // location,  (unit_per_box * num_of_box) comment
        SortedMap<String, String> locationDetail = new TreeMap<>(new LocationComparator());
        String sku;
        String desc;
        String fnSku;
        int totalUnitCount;

        public SkuLoc(String sku, String desc, String fnSku) {
            this.sku = sku;
            this.desc = desc;
            this.fnSku = fnSku;
            totalUnitCount = 0;
        }

        /**
         * Add location, unit per box, num of box, and comment to the locationDetail map.
         * Also update the totalUnitCount.
         *
         * When a location is added, it is appended to the existing location detail.
         * This is new update on July 2023.
         *
         * @param location
         * @param unitPerBox
         * @param numOfBox
         * @param comment
         */
        public void addLocation(String location, int unitPerBox, int numOfBox, String comment) {
            String tmpDetail = "(" + unitPerBox + " x " + numOfBox + " box = "
                    + unitPerBox * numOfBox + " ) " + comment + "-\n";

            String strDetail = null;
            if (locationDetail.get(location) != null) {
                //logger.warn("SKU {} at location {} already exist. Appending but please check.", sku, location);
                strDetail = locationDetail.get(location) + "..." + tmpDetail;
            } else
                strDetail = tmpDetail;

            locationDetail.put(location, strDetail);
            totalUnitCount = totalUnitCount + (unitPerBox * numOfBox);
        }

        public String getLocationDetail() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : locationDetail.entrySet()) {
                sb.append(entry.getKey());
                sb.append(" ");
                sb.append(entry.getValue());
            }
            return sb.toString();
        }
    }

    public static void main(String[] args) {
        boolean doWrite = DO_WIRTE;
        logger.info("Start InventoryFileConverterService - Used to be WarehosueLocationConverter");
        InventoryFileConverterService c = new InventoryFileConverterService(FILE_NAME);
        if (doWrite)
            c.createOrUpdateSkuLocSheet();
        else {
            Map<String, String> skuLocMap = c.getSkuLoc();
            System.out.println("skuLocMap size = " + skuLocMap.size());
            System.out.println(skuLocMap.toString());
            System.out.println("skuLocMap size = " + skuLocMap.size());
        }
    }
}

