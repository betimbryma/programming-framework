/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.smartsocietyproject.scenario2.handler;

import at.ac.tuwien.dsg.smartcom.callback.NotificationCallback;
import at.ac.tuwien.dsg.smartcom.exception.CommunicationException;
import at.ac.tuwien.dsg.smartcom.model.Identifier;
import at.ac.tuwien.dsg.smartcom.model.Message;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eu.smartsocietyproject.scenario2.Scenario2;
import eu.smartsocietyproject.scenario2.helper.RQAPlan;
import eu.smartsocietyproject.scenario2.helper.RQATaskResult;
import eu.smartsocietyproject.pf.ApplicationContext;
import eu.smartsocietyproject.pf.CollectiveWithPlan;
import eu.smartsocietyproject.pf.TaskResult;
import eu.smartsocietyproject.pf.cbthandlers.CBTLifecycleException;
import eu.smartsocietyproject.pf.cbthandlers.ExecutionHandler;
import eu.smartsocietyproject.scenario2.RQATaskRequest;
import eu.smartsocietyproject.scenario2.helper.ClosableIdentifier;
import eu.smartsocietyproject.smartcom.SmartComServiceImpl;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Svetoslav Videnov <s.videnov@dsg.tuwien.ac.at>
 */
public class RQAExecutionHandler implements ExecutionHandler, NotificationCallback {

    private RQAPlan plan;
    private RQATaskResult result = null;
    private ObjectMapper mapper = new ObjectMapper();
    private String orchestrationId = "RQA-orchestrator-";

    private Lock communityLock = new ReentrantLock();
    private Condition communityCondition = communityLock.newCondition();
    private Lock orchestrationLock = new ReentrantLock();
    private Condition orchestrationCondition = orchestrationLock.newCondition();
    private Semaphore communityMaxSemaphore = new Semaphore(0);

    public TaskResult execute(ApplicationContext context, CollectiveWithPlan agreed) throws CBTLifecycleException {

        this.resetHandler();
        //todo-sv: remove this cast
        SmartComServiceImpl sc = (SmartComServiceImpl) context.getSmartCom();
        Identifier callback = sc.registerNotificationCallback(this);

        try {
            //todo-sv: maybe registration of rest input adapter belongs also here
            //Identifier callback = sc.registerNotificationCallback(this);

            if (!(agreed.getPlan() instanceof RQAPlan)) {
                throw new CBTLifecycleException("Wrong plan!");
            }

            plan = (RQAPlan) agreed.getPlan();

            String conversationId = plan.getRequest().getId().toString();
            this.orchestrationId += conversationId;

            Properties props = new Properties();
            props.load(Scenario2.class.getClassLoader()
                    .getResourceAsStream("EmailAdapter.properties"));
            sc.addEmailPullAdapter(conversationId, props);
            sc.addEmailPullAdapter(orchestrationId, props);

            //send to human experts
            ObjectNode content = JsonNodeFactory.instance.objectNode();
            content.set("question", JsonNodeFactory.instance
                    .textNode(plan.getRequest().getRequest()));

            Message.MessageBuilder msg = new Message.MessageBuilder()
                    .setType("ask")
                    .setSubtype("question")
                    .setReceiverId(Identifier.collective(plan.getHumans().getId()))
                    .setSenderId(Identifier.component("RQA"))
                    .setConversationId(conversationId)
                    .setContent(mapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(content));

            sc.send(msg.create());

            //send to google peer
            content.set("resultCount", JsonNodeFactory.instance.numberNode(5));

            msg.setType("ask")
                    .setSubtype("question")
                    .setReceiverId(Identifier.peer(plan.getGoogle().getPeerId()))
                    .setSenderId(Identifier.component("RQA"))
                    .setConversationId(conversationId)
                    .setContent(mapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(content));

            sc.send(msg.create());

            communityLock.lock();
            try {
                //amount of community results we want for orchestrator
                //for demo purposes only set to 2
                communityMaxSemaphore.release(2);

                if (!communityCondition.await(plan.getRequest().getCommunityTime(),
                        plan.getRequest().getCommunityTimeUnit())) {
                    communityMaxSemaphore.drainPermits();
                }
            } finally {
                communityLock.unlock();
            }

            msg.setType("rqa")
                    .setSubtype("orchestrate")
                    .setContent(result.getResult())
                    .setSenderId(Identifier.component("RQA"))
                    .setConversationId(orchestrationId)
                    .setReceiverId(Identifier.peer(plan.getOrchestrator().getPeerId()));

            sc.send(msg.create());

            orchestrationLock.lock();

            if (!orchestrationCondition.await(plan.getRequest().getOrchestratorTime(),
                    plan.getRequest().getOrchestratorUnit())) {
                throw new CBTLifecycleException("Orchestrator did not respond in time!");
            }

            return result;
        } catch (InterruptedException ex) {
            //return in case result was allready processed at time of timeout
            if (result.QoR() == -1 || result.QoR() >= 0.75) {
                return result;
            }
            throw new CBTLifecycleException(ex);
        } catch (CommunicationException | IOException ex) {
            throw new CBTLifecycleException(ex);
//        } catch (Throwable e) {
//            //todo-sv: rmove this catch
//			e.printStackTrace();
//            throw new CBTLifecycleException(e);
        } finally {
            sc.unregisterNotificationCallback(callback);
            //todo-sv: also unregister email pull adapter
        }
    }

    private void resetHandler() {
        this.result = new RQATaskResult();
    }

    @Override
    public TaskResult getResultIfQoRGoodEnough() {
        if (this.result.isQoRGoodEnough()) {
            return result;
        }

        return null;
    }

    @Override
    public double resultQoR() {
        return result.QoR();
    }

    @Override
    public void notify(Message message) {
        if (orchestrationId.equals(message.getConversationId())
                && orchestrationLock.tryLock()) {
            result.setOrchestratorsChoice(message.getContent());
            orchestrationCondition.signal();
            orchestrationLock.unlock();
            return;
        }

        if (!plan.getRequest().getId().toString()
                .equals(message.getConversationId())) {
            return;
        }

        communityLock.lock();
        try {
            if (!communityMaxSemaphore.tryAcquire()) {
                return;
            }
        } finally {
            communityLock.unlock();
        }

        if (message.getSenderId().getId().equals(plan.getGoogle().getPeerId())) {
            result.setGoogleResult(message.getContent());
            return;
        }

        result.setHumanResult(message.getContent());

        if (communityMaxSemaphore.availablePermits() == 0) {
            communityCondition.signal();
        }
    }
}