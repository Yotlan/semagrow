package org.semagrow.cli;

import org.eclipse.rdf4j.query.*;
import org.eclipse.rdf4j.repository.RepositoryResolver;
import org.semagrow.repository.SemagrowRepositoryResolver;
import org.eclipse.rdf4j.query.resultio.*;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.config.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.stream.Collectors;
import org.apache.commons.cli.*;


/**
 * Created by angel on 27/11/2015.
 */
public class CliMain {

    private static final Logger logger = LoggerFactory.getLogger(CliMain.class);

    private static RepositoryResolver resolver = new SemagrowRepositoryResolver();

    public static void main(String[] args) throws IOException, ParseException {

        Options options = new Options();

        Option configFileOpt = new Option("c", "config", true,
            "config file, default is etc/default/semagrow/repository.ttl");

        Option metadataFileOpt = new Option("m", "metadata", true,
            "metadata file, default is etc/default/semagrow/metadata.ttl");

        Option queryFileOpt = new Option("q", "query", true,
            "query file");

        Option resultFileOpt = new Option("o", "output", true,
            "output file where results will be written");

        Option noExecOpt = new Option(null, "noexec", false,
            "if set, only the plan is generated, no query execution");

        options.addOption(configFileOpt);
        options.addOption(metadataFileOpt);
        options.addOption(queryFileOpt);
        options.addOption(resultFileOpt);
        options.addOption(noExecOpt);
        
        CommandLineParser parser = new DefaultParser();
        CommandLine line = parser.parse(options, args);

        String configFile = line.getOptionValue(configFileOpt.getOpt());
        String metadataFile = line.getOptionValue(metadataFileOpt.getOpt());
        String queryFile = line.getOptionValue(queryFileOpt.getOpt());
        String resultFile = line.getOptionValue(resultFileOpt.getOpt());
        boolean noExec = line.hasOption(noExecOpt.getLongOpt());

        String queryString = Files.lines(Paths.get(queryFile)).collect(Collectors.joining(System.lineSeparator()));

        String homeDir = Paths.get(resultFile).getParent().toString();
        String provenanceFile = homeDir + "/source_selection.txt";
        String explanationFile = homeDir + "/query_plan.txt";
		String sourceSelectionTimeFile = homeDir + "/source_selection_time.txt";
		String planningTimeFile = homeDir + "/planning_time.txt";
		String askFile = homeDir + "/ask.txt";
		String execTimeFile = homeDir + "/exec_time.txt";
        try (OutputStream cfgOut = new FileOutputStream(System.getProperty("user.dir")+"/config.properties")) {

            Properties prop = new Properties();

            // set the properties value
            prop.setProperty("config.file", configFile);
            prop.setProperty("metadata.file", metadataFile);
            prop.setProperty("ss.time.file", sourceSelectionTimeFile);
            prop.setProperty("ss.file", provenanceFile);
            prop.setProperty("planning.time.file", planningTimeFile);
            prop.setProperty("ask.file", askFile);

            // save properties to project root folder
            prop.store(cfgOut, null);

            System.out.println(prop);

        }

        // logger.debug("Using configuration from {}", repositoryConfig);
        logger.debug("Writing result to file {}", resultFile);
        logger.debug("Evaluating query {}", queryString);

        Repository repository = null;
        try {
            repository = resolver.getRepository(null);

            repository.init();
            RepositoryConnection conn = repository.getConnection();
            TupleQuery query = (TupleQuery) conn.prepareTupleQuery(QueryLanguage.SPARQL, queryString);

            try (BufferedWriter explain_writer = new BufferedWriter(new FileWriter(new File(line.getOptionValue(explanationFile))))){
                explain_writer.write(String.valueOf(query));
            }

            TupleQueryResultWriter outputWriter = getWriter(resultFile);

            long t1 = System.currentTimeMillis();
            if(!noExec) {
                query.evaluate(outputWriter);
                long t2 = System.currentTimeMillis();

                try (BufferedWriter exec_time_writer = new BufferedWriter(new FileWriter(new File(execTimeFile)))) {
                    exec_time_writer.write(String.valueOf(t2-t1));
                }
            }

            logger.debug("Closing connection");
            conn.close();

            logger.debug("Shutting down repository");
            repository.shutDown();

        } catch (RepositoryConfigException e) {
            e.printStackTrace();
        } catch (RepositoryException e) {
            e.printStackTrace();
        } catch (MalformedQueryException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (QueryEvaluationException e) {
            e.printStackTrace();
        } catch (TupleQueryResultHandlerException e) {
            e.printStackTrace();
        }

    }

    private static TupleQueryResultWriter getWriter(String resultFile) throws FileNotFoundException {

        OutputStream outStream = new FileOutputStream(resultFile);

        QueryResultFormat writerFormat = TupleQueryResultWriterRegistry.getInstance().getFileFormatForFileName(resultFile).get();
        TupleQueryResultWriterFactory writerFactory = TupleQueryResultWriterRegistry.getInstance().get(writerFormat).get();
        return writerFactory.getWriter(outStream);

    }
}
