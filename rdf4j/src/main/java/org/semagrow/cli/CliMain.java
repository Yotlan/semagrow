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

/**
 * Created by angel on 27/11/2015.
 */
public class CliMain {

    private static final Logger logger = LoggerFactory.getLogger(CliMain.class);

    private static RepositoryResolver resolver = new SemagrowRepositoryResolver();

    public static void main(String[] args) throws IOException {

        // FIXME: argParser for the command-line arguments
        /*
           Usage: runSemagrow -c repository.ttl -q "SELECT..." -o output.json
         */
        // String repositoryConfig = args[0];
        // String queryString = args[1];
        // String resultFile = args[2];

        String resultFile = args[0];
		String provenancefile = args[1];
		String explanationfile = args[2];
		String queryFile = args[3];
        String noExec = args[4];

        String queryString = Files.lines(Paths.get(queryFile)).collect(Collectors.joining(System.lineSeparator()));

        String homeDir = Paths.get(provenancefile).getParent().toString();
		String sourceSelectionTimeFile = homeDir + "/source_selection_time.txt";
		String planningTimeFile = homeDir + "/planning_time.txt";
		String askFile = homeDir + "/ask.txt";
		String execTimeFile = homeDir + "/exec_time.txt";
        try (OutputStream cfgOut = new FileOutputStream(System.getProperty("user.dir")+"/config.properties")) {

            Properties prop = new Properties();

            // set the properties value
            prop.setProperty("ss.time.file", sourceSelectionTimeFile);
            prop.setProperty("ss.file", provenancefile);
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

            try (BufferedWriter explain_writer = new BufferedWriter(new FileWriter(new File(explanationfile)))){
                explain_writer.write(String.valueOf(query));
            }

            TupleQueryResultWriter outputWriter = getWriter(resultFile);

            long t1 = System.currentTimeMillis();
            if(!Boolean.valueOf(noExec)){
                // query.setMaxExecutionTime(Integer.valueOf(timeout));
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
