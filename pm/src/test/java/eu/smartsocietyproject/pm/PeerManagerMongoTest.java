/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.smartsocietyproject.pm;

import com.mongodb.client.MongoCollection;
import eu.smartsocietyproject.peermanager.Peer;
import eu.smartsocietyproject.peermanager.helper.SimplePeer;
import eu.smartsocietyproject.peermanager.helper.ResidentCollectiveIntermediary;
import eu.smartsocietyproject.pf.ApplicationBasedCollective;
import eu.smartsocietyproject.pf.Attribute;
import eu.smartsocietyproject.pf.CollectiveBase;
import eu.smartsocietyproject.pf.ResidentCollective;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Svetoslav Videnov <s.videnov@dsg.tuwien.ac.at>
 */
public class PeerManagerMongoTest {

    private final String expextedMembersKey = "peers";
    private final String expectedMemberTim = "tim";
    private final String expectedMemberTom = "tom";
    private final String expectedMemberTum = "tum";
    private final String expectedAttUserNameKey = "username";
    private final String expectedAttAgeKey = "age";
    private final String expectedUsername = "testUser";
    private final int expectedAge = 29;
    private final String expectedCollectiveId = "test123";
    private final String expectedDBCollectionKey = "collective";
    private final String expectedDBIdKey = "id";
    private final String expectedDBAttributesKey = "attributes";

    public PeerManagerMongoTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testClose() {
    }
    
    private CollectiveBase getCollective() {
        List<Peer> members = new ArrayList<>();
        members.add(new SimplePeer(this.expectedMemberTim));
        members.add(new SimplePeer(this.expectedMemberTom));
        members.add(new SimplePeer(this.expectedMemberTum));

        Map<String, Attribute> attributes = new HashMap<>();
        attributes.put(this.expectedAttUserNameKey,
                new TestStringAttribute(this.expectedUsername));
        attributes.put(this.expectedAttAgeKey,
                new TestIntAttribute(this.expectedAge));
        
        return new TestCollective(this.expectedCollectiveId, members,
                        attributes);
    }

    @Test
    public void testPersistCollective() throws IOException {
        PeerManagerMongo pm = new PeerManagerMongo(6666);
        pm.persistCollective(this.getCollective());

        //load actual collective from DB
        MongoCollection<Document> collectivesCollection = pm.getMongoDb()
                .getCollection(this.expectedDBCollectionKey);
        
        assertEquals(1, collectivesCollection.count());
        
        Document actualCollective = collectivesCollection.find().first();
        assertEquals(this.expectedCollectiveId, 
                actualCollective.get(this.expectedDBIdKey));
        
        List<String> actualMembers = actualCollective
                .get(this.expextedMembersKey, List.class);
        assertEquals(3, actualMembers.size());
        for(String actualMember: actualMembers) {
            assertTrue(this.expectedMemberTim.equals(actualMember) 
                    || this.expectedMemberTom.equals(actualMember)
                    || this.expectedMemberTum.equals(actualMember));
        }
        
        Map<String, Document> actualAttributes = actualCollective
                .get(this.expectedDBAttributesKey, Map.class);
        assertEquals(2, actualAttributes.size());
        assertTrue(actualAttributes.containsKey(this.expectedAttAgeKey));
        assertTrue(actualAttributes.containsKey(this.expectedAttUserNameKey));
        
        for(Document doc: actualAttributes.values()) {
            assertTrue(doc.containsKey("value"));
            assertTrue(doc.containsKey("type"));
        }
        
        assertEquals(String.valueOf(this.expectedAge), actualAttributes
                .get(this.expectedAttAgeKey).get("value"));
        assertEquals(TestIntAttribute.class.getName(), actualAttributes
                .get(this.expectedAttAgeKey).get("type"));
        
        assertEquals(this.expectedUsername, actualAttributes
                .get(this.expectedAttUserNameKey).get("value"));
        assertEquals(TestStringAttribute.class.getName(), actualAttributes
                .get(this.expectedAttUserNameKey).get("type"));
    }
    
    @Test
    public void testReadCollective() throws IOException {
        PeerManagerMongo pm = new PeerManagerMongo(6666);
        pm.persistCollective(this.getCollective());
        
        ResidentCollectiveIntermediary collective = 
                pm.readCollectiveById(this.expectedCollectiveId);
        assertNotNull(collective);
        
        Collection<Peer> members = collective.getMembers();
        assertEquals(3, members.size());
        for(Peer actualMember: members) {
            assertTrue(this.expectedMemberTim.equals(actualMember.getId()) 
                    || this.expectedMemberTom.equals(actualMember.getId())
                    || this.expectedMemberTum.equals(actualMember.getId()));
        }
        
        Map<String, Attribute> actualAttributes = collective.getAttributes();
        assertEquals(2, actualAttributes.size());
        assertTrue(actualAttributes.containsKey(this.expectedAttAgeKey));
        assertTrue(actualAttributes.containsKey(this.expectedAttUserNameKey));
        
        assertTrue(actualAttributes.get(this.expectedAttAgeKey) 
                instanceof TestIntAttribute);
        assertTrue(actualAttributes.get(this.expectedAttUserNameKey) 
                instanceof TestStringAttribute);
        
        assertEquals(String.valueOf(this.expectedAge), actualAttributes
                .get(this.expectedAttAgeKey).toString());
        assertEquals(this.expectedUsername, actualAttributes
                .get(this.expectedAttUserNameKey).toString());
    }

    @Test
    public void testReadCollectiveByQuery() throws IOException {
    }
    
    //todo-sv: pm init has to be between each test!

}
