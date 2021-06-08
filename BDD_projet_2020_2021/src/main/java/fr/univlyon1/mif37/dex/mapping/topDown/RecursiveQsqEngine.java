package fr.univlyon1.mif37.dex.mapping.topDown;

import fr.univlyon1.mif37.dex.mapping.Atom;
import fr.univlyon1.mif37.dex.mapping.Mapping;
import fr.univlyon1.mif37.dex.mapping.Tgd;
import fr.univlyon1.mif37.dex.mapping.Variable;

import java.util.*;
/**
 * @juba BDD
 */
/**
 * A Datalog evaluation engine that uses a recursive version of the
 * query-subquery top-down technique.
 *
 */
public class RecursiveQsqEngine {
    public Relation result;
    /**
     * A container for tracking global information passed back and forth between
     * recursion frames.
     *
     */
    private class QSQRState {
        /**
         * Tracks the answer tuples generated for each adorned predicate.
         */
        private Map<AdornedAtom,Relation> ans;
        /**
         * Tracks which input tuples have been used for each rule.
         */
        private Map<AdornedAtom,Relation> inputByRule;
        /**
         * Holds all the adorned rules for a given adorned predicate.
         */
        private Map<AdornedAtom, List<AdornedTgd>> adornedRules;
        /**
         * Holds all the unadorned rules for a given predicate.
         */
        //private final Map<Object,Object>  unadornedRules;


        /**
         * Initializes state with a set of all unadorned rules for the program.
         *
         * @param adornedRules
         *            set of unadorned rules
         */
        public QSQRState(Map<AdornedAtom,List<AdornedTgd>> adornedRules) {
            this.adornedRules = adornedRules;
            this.ans = new HashMap<>();
            this.inputByRule = new HashMap<>();

            for(Map.Entry<AdornedAtom, List<AdornedTgd>> map: adornedRules.entrySet()) {
                ans.put(map.getKey(), new Relation((List<Variable>) map.getKey().getAtom().getVars()));
                inputByRule.put(map.getKey(), new Relation(map.getKey().getBoundVariables()));
            }
        }

    }

    public RecursiveQsqEngine(Mapping mapping) {
        AdornedRules adornedRules = new AdornedRules(mapping);
        QSQRState state = new QSQRState(adornedRules.adornedMap());

        // Step 1 :
        // Identify the adorned rules with relevant P predicate in the body of the query
        AdornedAtom query = null;
        List<AdornedAtom> body = new ArrayList<>();
        for(Map.Entry<AdornedAtom, List<AdornedTgd>> rule : state.adornedRules.entrySet()) {
            if(rule.getKey().getAtom().getName().equals("query")) {
                query = rule.getKey();
                body = rule.getValue().get(0).getBody();
            }
        }

        List<Variable> cstAttributes = (List<Variable>) body.get(0).getAtom().getVars();
        List<Tuple>  cstTuples = new ArrayList<>();
        for(fr.univlyon1.mif37.dex.mapping.Relation r : mapping.getEDB()) {
            if(r.getName().equals("cst")){
                cstTuples.add(new Tuple(Arrays.asList(r.getAttributes())));
            }
        }
        // Calculate Input_Predicate
        AdornedAtom predicate = body.get(1);
        state.inputByRule.replace(predicate, new Relation(cstAttributes, cstTuples));

        for(AdornedTgd rule : state.adornedRules.get(predicate)){
            //System.out.println(rule);
            qsqrSubroutine(rule, state.inputByRule.get(predicate), state, mapping);
        }
        //System.out.println(state.ans.get(predicate));
        // Projection of Output_predicate on query's variables
        result = state.ans.get(predicate).projection((List<Variable>) query.getAtom().getVars());
        // return the result
    }


    /**
     * Evaluates the supplied rule using the input tuples newInput.
     *
     * @param rule
     *            rule
     * @param newInput
     *            input tuples
     * @param state
     *            current state of evaluation-wide variables
     */
    private void qsqrSubroutine(AdornedTgd rule, Relation newInput, QSQRState state, Mapping map) {
        System.out.println("rule : " + rule);
        System.out.println(newInput);

        QsqTemplate qsqTemplate = new QsqTemplate(rule);
        List<Relation> sup = new ArrayList<>();
        for(TermSchema t: qsqTemplate.getSchemata()) {
            sup.add(new Relation(t));
        }

        //sup0
        sup.get(0).tuples.addAll(newInput.tuples);
        System.out.println(sup.get(0));
        int i = 1;
        //sup1 - supn
        for(AdornedAtom atom: rule.getBody()){
            // Case EDB
            if(map.getEdbNames().contains(atom.getAtom().getName())) {
                System.out.println("je suis un EDB");
                Relation edb = new Relation(atom.getAtom().getName(), (List<Variable>) atom.getAtom().getVars(), map);
                System.out.println(edb);
                //reste la jointure entre sup_i-1 et edb puis projection sur les variables de sup_i
                Relation result= edb.join(sup.get(i-1)).projection(sup.get(i).attributes.attributes);
                System.out.println(result);
                sup.get(i).tuples.addAll(result.tuples);
                System.out.println(sup.get(i));
            }
            // Case IDB
            else {
                // faut faire une projection de sup_i-1 sur boundVars de IDB - Input_IDB = S
                System.out.println("sup i-1 : " + sup.get(i-1));
                System.out.println("bound variables of predicate : " + atom.getBoundVariables());
                System.out.println("sup i-1 projection on bound : " + sup.get(i-1).projection(atom.getBoundVariables()));
                System.out.println("input_predicate : " + state.inputByRule.get(atom));
                Relation s = sup.get(i-1).projection(atom.getBoundVariables()).substract(state.inputByRule.get(atom));
                System.out.println(sup.get(i-1).projection(atom.getBoundVariables()) + " - " + state.inputByRule.get(atom) + " = " + s);

                // faut faire Input_IDB = Input_IDB U S
                if(!s.tuples.isEmpty()) {
                    state.inputByRule.get(atom).tuples.addAll(s.tuples);
                    System.out.println(state.inputByRule.get(atom));
                    //System.exit(0);
                    for(AdornedTgd r:state.adornedRules.get(atom)){
                        // lancer l'appel récursif
                        /*long startTime = System.nanoTime() / 1000000;
                        long endTime = System.nanoTime() / 1000000;
                        while(startTime < endTime + 5000) {
                            startTime = System.nanoTime() / 1000000;
                        }*/
                        System.out.println("--------------------------------------");
                        qsqrSubroutine(rule, state.inputByRule.get(atom), state, map);
                    }
                }
                // sup_i = sup_i join output_IDB
                Relation sup_i = sup.get(i-1).join(state.ans.get(atom).projection(sup.get(i).attributes.attributes));
                sup.get(i).tuples.addAll(sup_i.tuples);
            }
            i++;
        }

        //supn
        state.ans.get(rule.getHead()).tuples.addAll(sup.get(sup.size()-1).tuples);

    }



    /**
     *
     *
     * Preparation of query q and retun the obtained result
     *
     *
    **/

    public Set<Object> query(Atom q) {
        Set<Object> result = new LinkedHashSet<>();
        return result;
    }

    /**
     * Evaluates the query represented by the adorned predicate p and the
     * relation newInput.
     *
     * @param p
     *            adorned predicate of query
     * @param newInput
     *            input tuples
     * @param state
     *            current state of evaluation-wide variables
     */
    private void qsqr(AdornedAtom p, Relation newInput, QSQRState state) {

    }



}
