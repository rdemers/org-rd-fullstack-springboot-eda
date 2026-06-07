/*
 * Copyright 2026; Réal Demers.
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
package org.rd.fullstack.springbooteda;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.rd.fullstack.springbooteda.config.Application;
import org.rd.fullstack.springbooteda.dao.InventoryRepository;
import org.rd.fullstack.springbooteda.dto.InventoryView;
import org.rd.fullstack.springbooteda.util.JasperUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.util.Assert;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.json.data.JsonDataSource;

/*
 * See POM.XML file
 * - Plugins section: maven-surefire-plugin
 * - Unit tests VS integrated tests.
 */
@SpringBootTest(classes = Application.class)
@WebAppConfiguration
// @ActiveProfiles("test") If specific configuration file. Example: application-test.yml
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Utility tests for generating reports.")
public class T1010_JasperUtils_UT_Tests {

    private static final Logger logger = 
        LoggerFactory.getLogger(T1010_JasperUtils_UT_Tests.class);

    private static final String target      = "./target/InventsReport";
    private static final String jrxmlReport = "jasper/invent-report.jrxml";
    
    @Autowired
    private InventoryRepository inventoryRepository;

    @Test
    public void generateInventsReport() throws Exception {
        logger.info("Start of execution.");

        // Get the free ones for our report.
        List<InventoryView> invents = new ArrayList<InventoryView>();
        inventoryRepository.findAllView().forEach(invents::add);

        // Jackson can serialize/deserialize most of your classes without requiring configuration
        // particular. We construct a series of POJO/JAVA objects to produce a JSON string.
        // This string is then used to reconstruct the object.
        ObjectMapper objectMapper = new ObjectMapper();
        String strJSONReport = objectMapper.writeValueAsString(invents);
        logger.info(strJSONReport); // Produce a JSON string.

        // JASPER ...
        // A report (.JRXML) must be compiled by JASPER in order to use it to produce a report.
        // This process is long and can be done at compile time. We have included a plugin that
        // do this work. See: POM.XML. For demonstration purposes, we perform a compilation
        // by hand. This compilation is normally kept with the same name as the source. However, the extension
        // will be (.JASPER).

        JasperReport jasperReport = null;
        try {
            jasperReport = JasperUtils.compileReport(jrxmlReport, true);
        } catch (Exception ex) {
            logger.info("Exception occurred while compiling report: {}.", ex);
        }
        Assert.notNull(jasperReport, "jasperReport == NULL");

        // Produce the report
        // =====================================================================================================
        // The production of the report consists of using the compiled version (.JASPER) with a data source and
        // parameters (optional). This production will produce a report in (.JRPRINT) format. The latter is used
        // in order to produce the final output: pdf, xls, html or csv.

        // Data source. JRDatasource. See documentation. PDF attached; doc directory.
        // Our JSON datasource has multiple branches. We specify the branch to use.

        JsonDataSource datasource = new JsonDataSource(new java.io.ByteArrayInputStream(strJSONReport.getBytes("UTF-8")));

        // Our settings. The title only for demonstration.
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("title", "Corroboration report");
        parameters.put("sub-title", "The inventories state report");

        // Production of the report.
        JasperPrint jasperPrint = null;
        try {
            jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, datasource);
        } catch (Exception ex) {
            logger.info("Exception occurred while filling report: {}.", ex);
        }
        Assert.notNull(jasperPrint, "jasperPrint == NULL");

        // Our report is complete. There are different ways to export it.
        JasperUtils.exportToPdf(jasperPrint, target + ".pdf");
        JasperUtils.exportToXlsx(jasperPrint,target + ".xlsx", "Report");
        JasperUtils.exportToCsv(jasperPrint, target + ".csv");
        JasperUtils.exportToHtml(jasperPrint,target + ".html");
 
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.reset(); // Make sure you are at zero.

        JasperUtils.exportToPdf(jasperPrint, baos);
        Assert.isTrue((baos.toByteArray().length != 0), "No report in buffer.");

        logger.info("End of execution.");
    }
}