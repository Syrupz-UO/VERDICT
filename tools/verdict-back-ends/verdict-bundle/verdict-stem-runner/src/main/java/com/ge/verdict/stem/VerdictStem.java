/* See LICENSE in project directory */
package com.ge.verdict.stem;

import com.ge.research.sadl.importer.TemplateException;
import com.ge.research.sadl.model.visualizer.GraphVizVisualizer;
import com.ge.research.sadl.model.visualizer.IGraphVisualizer.Orientation;
import com.ge.research.sadl.reasoner.ConfigurationException;
import com.ge.research.sadl.reasoner.InvalidNameException;
import com.ge.research.sadl.reasoner.QueryCancelledException;
import com.ge.research.sadl.reasoner.QueryParseException;
import com.ge.research.sadl.reasoner.ReasonerNotFoundException;
import com.ge.research.sadl.reasoner.ResultSet;
import com.ge.research.sadl.server.ISadlServer;
import com.ge.research.sadl.server.SessionNotFoundException;
import com.ge.research.sadl.server.server.SadlServerImpl;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Runs SADL on a Verdict STEM project. */
public class VerdictStem {
    /**
     * Runs SADL on a Verdict STEM project.
     *
     * @param projectDir Path to Verdict STEM project
     * @param outputDir Output directory to write to
     * @param graphsDir Graphs directory to write to
     */
    public void runStem(File projectDir, File outputDir, File graphsDir) {
        try {
            final Path knowledgeBaseDir = projectDir.toPath();
            final Path csvDataDir = knowledgeBaseDir.resolve("CSVData");
            final boolean csvIncludesHeader = true;
            final Path busCsv = csvDataDir.resolve("ScnBusBindings.csv");
            final Path compCsv = csvDataDir.resolve("ScnCompProps.csv");
            final Path connCsv = csvDataDir.resolve("ScnConnections.csv");
            final Path archMitigationCsv = outputDir.toPath().resolve("ArchMitigation.csv");
            final Path capecCsv = outputDir.toPath().resolve("CAPEC.csv");
            final Path defensesCsv = outputDir.toPath().resolve("Defenses.csv");
            final Path connDefensesCsv = outputDir.toPath().resolve("ConnDefenses.csv");
            final Path defenses2NistCsv = outputDir.toPath().resolve("Defenses2NIST.csv");
            final String graphName = "Run_sadl_graph";
            final Path modelsDir = knowledgeBaseDir.resolve("OwlModels");
            final Path templatesDir = knowledgeBaseDir.resolve("Templates");
            final Path busTemplate = templatesDir.resolve("ScnBusBindings.tmpl");
            final Path compTemplate = templatesDir.resolve("ScnCompProps.tmpl");
            final Path connTemplate = templatesDir.resolve("ScnConnections.tmpl");

            final String modelName = "http://sadl.org/STEM/Run";
            final String instanceDataNamespace = "http://sadl.org/STEM/Scenario#";
            final String anyNamespace = "http[^#]*#";

            ISadlServer srvr = new SadlServerImpl(knowledgeBaseDir.toString());
            srvr.selectServiceModel(modelsDir.toString(), modelName);
            srvr.setInstanceDataNamespace(instanceDataNamespace);

            srvr.loadCsvData(
                    compCsv.toUri().toString(), csvIncludesHeader, compTemplate.toUri().toString());
            srvr.loadCsvData(
                    connCsv.toUri().toString(), csvIncludesHeader, connTemplate.toUri().toString());
            srvr.loadCsvData(
                    busCsv.toUri().toString(), csvIncludesHeader, busTemplate.toUri().toString());

            // Create output and graph directories
            outputDir.mkdirs();
            graphsDir.mkdirs();

            ResultSet rs = srvr.query("http://sadl.org/STEM/Queries#Defenses2NIST");
            if (rs != null) {
                Files.write(
                        defenses2NistCsv,
                        rs.toString()
                                .replaceAll(anyNamespace, "")
                                .getBytes(StandardCharsets.UTF_8));
            }

            rs = srvr.query("http://sadl.org/STEM/Queries#CAPEC");
            if (rs != null) {
                Files.write(
                        capecCsv,
                        rs.toString()
                                .replaceAll(anyNamespace, "")
                                .getBytes(StandardCharsets.UTF_8));
            }

            rs = srvr.query("http://sadl.org/STEM/Queries#Defenses");
            if (rs != null) {
                Files.write(
                        defensesCsv,
                        rs.toString()
                                .replaceAll(anyNamespace, "")
                                .getBytes(StandardCharsets.UTF_8));
            }

            rs = srvr.query("http://sadl.org/STEM/Queries#ConnDefenses");
            if (rs != null) {
                Files.write(
                        connDefensesCsv,
                        rs.toString()
                                .replaceAll(anyNamespace, "")
                                .getBytes(StandardCharsets.UTF_8));
            }

            rs = srvr.query("http://sadl.org/STEM/Queries#ArchMitigation");
            if (rs != null) {
                Files.write(
                        archMitigationCsv,
                        rs.toString()
                                .replaceAll(anyNamespace, "")
                                .getBytes(StandardCharsets.UTF_8));
            }

            rs = srvr.query("http://sadl.org/STEM/Queries#STEMgraph");
            if (rs != null) {
                GraphVizVisualizer visualizer = new GraphVizVisualizer();
                visualizer.initialize(
                        graphsDir.getPath(),
                        graphName,
                        graphName,
                        null,
                        Orientation.TD,
                        "STEM (Graph)");
                visualizer.graphResultSetData(rs);
            }
        } catch (IOException
                | InvalidNameException
                | SessionNotFoundException
                | QueryCancelledException
                | QueryParseException
                | ReasonerNotFoundException
                | ConfigurationException
                | URISyntaxException
                | TemplateException e) {
            throw new RuntimeException(e);
        }
    }
}
