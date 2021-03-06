/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.smartsocietyproject.peermanager.query;

/**
 * Up to the name it is the same as the PeerQuery.
 * It is represented by a separate class to illustrate that one of the PM functions
 * queries peers and the other queries collectives.
 * @author Svetoslav Videnov <s.videnov@dsg.tuwien.ac.at>
 */
public class CollectiveQuery extends Query {
    public static CollectiveQuery create() {
        return new CollectiveQuery();
    }

    @Override
    public CollectiveQuery withRule(QueryRule rule) {
        super.withRule(rule);
        return this;
    }
    
    
}
