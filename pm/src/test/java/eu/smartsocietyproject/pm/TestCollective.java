/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.smartsocietyproject.pm;

import eu.smartsocietyproject.peermanager.Peer;
import eu.smartsocietyproject.pf.ApplicationBasedCollective;
import eu.smartsocietyproject.pf.Attribute;
import eu.smartsocietyproject.pf.Collective;
import eu.smartsocietyproject.pf.CollectiveBase;
import java.util.Collection;
import java.util.Map;

/**
 *
 * @author Svetoslav Videnov <s.videnov@dsg.tuwien.ac.at>
 */
public class TestCollective extends CollectiveBase implements Collective {
    
    public TestCollective(String id,
            Collection<Peer> members,
            Map<String, ? extends Attribute> attributes) {
        super(null, id, null, members, attributes);
    }

    @Override
    public ApplicationBasedCollective toApplicationBasedCollective() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
