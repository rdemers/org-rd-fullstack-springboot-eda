/*
 * Copyright 2026, 2025; Réal Demers.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.rd.fullstack.springbooteda.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.export.HtmlExporter;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.engine.util.JRSaver;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleHtmlExporterOutput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleWriterExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;

public class JasperUtils {
    private static final Logger logger = LoggerFactory.getLogger(JasperUtils.class);
    private JasperUtils() { } // Static usage only.

    static public JasperReport compileReport(String reportSource) throws IOException, JRException, URISyntaxException, FileNotFoundException {
        return compileReport(reportSource, false);
    }

    static public JasperReport compileReport(String reportSource, boolean bFile) throws IOException, JRException, URISyntaxException, FileNotFoundException {
        Assert.notNull(reportSource, "JasperUtils::compileReport - reportSource is NULL.");
        File file = ResourceUtils.getFile("classpath:" + reportSource);

        // Determine source and target.
        String jasperReportSource = file.getAbsolutePath();
        String jasperReportTarget = jasperReportSource.replace(".jrxml", ".jasper");
        logger.info("Compiling Jasper report from source {} to target {}.", jasperReportSource, jasperReportTarget);

        // Compilation of the report in ".jasper".
        JasperReport jasperReport;
        try (InputStream reportStream = new FileInputStream(file)) {
            jasperReport = JasperCompileManager.compileReport(reportStream);
        }

        if (bFile) // The source must be on the PATH. But not in a JAR (responsibility of the developer) ;-)
            JRSaver.saveObject(jasperReport, jasperReportTarget);

        return jasperReport;
    }

    public static void exportToPdf(JasperPrint jasperPrint, String pdfTarget) throws JRException {
        Assert.notNull(jasperPrint, "JasperUtils::exportToPdf - jasperPrint is NULL.");
        Assert.notNull(pdfTarget, "JasperUtils::exportToPdf - target is NULL.");

        JasperExportManager.exportReportToPdfFile(jasperPrint, pdfTarget);
    }

    public static void exportToPdf(JasperPrint jasperPrint, ByteArrayOutputStream pdfTarget) throws JRException {
        Assert.notNull(jasperPrint, "JasperUtils::exportToPdf - jasperPrint is NULL.");
        Assert.notNull(pdfTarget, "JasperUtils::exportToPdf - target is NULL.");

        JasperExportManager.exportReportToPdfStream(jasperPrint, pdfTarget);
    }
   
    public static void exportToXlsx(JasperPrint jasperPrint, String xlsTarget, String xlsSheetName) throws JRException {
        Assert.notNull(jasperPrint, "JasperUtils::exportToXlsx - jasperPrint is NULL.");
        Assert.notNull(xlsTarget, "JasperUtils::exportToXlsx - xlsTarget is NULL.");
        Assert.notNull(xlsSheetName, "JasperUtils::exportToXlsx - xlsSheetName is NULL.");

        JRXlsxExporter exporter = new JRXlsxExporter();

        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(xlsTarget));

        SimpleXlsxReportConfiguration reportConfig = new SimpleXlsxReportConfiguration();
        reportConfig.setSheetNames(new String[] { xlsSheetName });

        exporter.setConfiguration(reportConfig);
        exporter.exportReport();
    }
    
    public static void exportToCsv(JasperPrint jasperPrint, String csvTarget) throws JRException {
        Assert.notNull(jasperPrint, "JasperUtils::exportToCsv - jasperPrint is NULL.");
        Assert.notNull(csvTarget, "JasperUtils::exportToCsv - csvTarget is NULL.");

        JRCsvExporter exporter = new JRCsvExporter();

        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setExporterOutput(new SimpleWriterExporterOutput(csvTarget));

        exporter.exportReport();
     }

    public static void exportToHtml(JasperPrint jasperPrint, String htmlTarget) throws JRException {
        Assert.notNull(jasperPrint, "JasperUtils::exportToHtml - jasperPrint is NULL.");
        Assert.notNull(htmlTarget, "JasperUtils::exportToHtml - htmlTarget is NULL.");

        HtmlExporter exporter = new HtmlExporter();

        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setExporterOutput(new SimpleHtmlExporterOutput(htmlTarget));

        exporter.exportReport();
    }
}