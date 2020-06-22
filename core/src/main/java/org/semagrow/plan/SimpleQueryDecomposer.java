package org.semagrow.plan;

import org.eclipse.rdf4j.query.algebra.helpers.StatementPatternCollector;
import org.semagrow.plan.optimizer.ExtensionOptimizer;
import org.semagrow.plan.optimizer.LimitPushDownOptimizer;
import org.semagrow.plan.util.BPGCollector;
import org.semagrow.estimator.CostEstimatorResolver;
import org.semagrow.estimator.CardinalityEstimatorResolver;
import org.semagrow.plan.util.BindingSetAssignmentCollector;
import org.semagrow.selector.StaticSourceSelector;
import org.semagrow.selector.SourceSelector;

import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.Dataset;
import org.eclipse.rdf4j.query.algebra.TupleExpr;
import org.eclipse.rdf4j.query.algebra.evaluation.QueryOptimizer;
import org.eclipse.rdf4j.query.algebra.evaluation.util.QueryOptimizerList;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;


/**
 * Dynamic Programming Query Decomposer
 *
 * <p>Dynamic programming implementation of the
 * eu.semagrow.core.decomposer.QueryDecomposer interface.</p>
 *
 * @author Angelos Charalambidis
 * @author Stasinos Konstantopoulos
 */
@Deprecated
public class SimpleQueryDecomposer implements QueryDecomposer
{

	private org.slf4j.Logger logger =
			org.slf4j.LoggerFactory.getLogger( SimpleQueryDecomposer.class );

	private CostEstimatorResolver costEstimator;
    private CardinalityEstimatorResolver cardinalityEstimator;
    private SourceSelector sourceSelector;

    public SimpleQueryDecomposer(CostEstimatorResolver estimator,
                                 CardinalityEstimatorResolver cardinalityEstimator,
                                 SourceSelector selector)
    {
        this.costEstimator = estimator;
        this.sourceSelector = selector;
        this.cardinalityEstimator = cardinalityEstimator;
    }


    /**
     * This method is the entry point to the Semagrow Stack that is called
     * by the HTTP endpoint implementation in eu.semagrow.stack.webapp
     * <p>
     * This methods edits {@code expr} in place to decompose it into the
     * sub-expressions that will be executed at each data source and to
     * annotate it with the execution plan.
     *
	 * @param expr The expression that will be decomposed. It must be an instance of eu.semagrow.core.impl.algebra.QueryRoot
	 * @param dataset
	 * @param bindings
     */

    @Override
    public void decompose(TupleExpr expr, Dataset dataset, BindingSet bindings)
    {
        /*
         * Identify the Basic Graph Patterns, a partitioning of the AST into
         * BGP sub-trees such that each BGP only uses the operators that the decomposer
         * can handle.
         */

        PlanFactory planFactory = new SimplePlanFactory(costEstimator, cardinalityEstimator);

        Collection<TupleExpr> basicGraphPatterns = BPGCollector.process(expr);

        for(TupleExpr bgp : basicGraphPatterns) {

        	/* creates the context of operation of the decomposer.
        	 * Specifically, collects FILTER statements */
            DecomposerContext ctx = new DecomposerContext( bgp );

            SourceSelector staticSelector = new StaticSourceSelector(sourceSelector.getSources(bgp, dataset, bindings));


        	/* uses the SourceSelector provided in order to identify the
        	 * sub-expressions that can be executed at each data source,
        	 * and annotates with cardinality and selectivity metadata */
            /*PlanGenerator planGenerator =
            		new PlanGeneratorImpl( ctx, sourceSelector, costEstimator, cardinalityEstimator );*/

            PlanGenerator planGenerator = new SimplePlanGenerator(ctx, sourceSelector, planFactory);

        	/* optimizes the plans generated by the PlanGenerator */
            DPPlanOptimizer planOptimizer = new DPPlanOptimizer(planGenerator);

        	/* selects the optimal plan  */
            Collection<TupleExpr> exprs = new HashSet<>();
            exprs.addAll(BindingSetAssignmentCollector.process(bgp));
            exprs.addAll(StatementPatternCollector.process(bgp));
            Optional<Plan> maybePlan = planOptimizer.getBestPlan(exprs, bindings, dataset);

            if (maybePlan.isPresent()) {
                /* grafts the optimal plan into expr */
                bgp.replaceWith(maybePlan.get());
            }
        }

        QueryOptimizer opt = new QueryOptimizerList(
                new ExtensionOptimizer(),
                new LimitPushDownOptimizer());

        opt.optimize(expr, dataset, bindings);
    }


}
