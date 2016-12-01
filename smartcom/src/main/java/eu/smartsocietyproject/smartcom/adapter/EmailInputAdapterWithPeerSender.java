/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.smartsocietyproject.smartcom.adapter;

import at.ac.tuwien.dsg.smartcom.adapter.InputPullAdapter;
import at.ac.tuwien.dsg.smartcom.adapter.exception.AdapterException;
import at.ac.tuwien.dsg.smartcom.adapters.EmailInputAdapter;
import at.ac.tuwien.dsg.smartcom.adapters.email.MailUtils;
import at.ac.tuwien.dsg.smartcom.model.Identifier;
import at.ac.tuwien.dsg.smartcom.model.Message;
import at.ac.tuwien.dsg.smartcom.model.PeerChannelAddress;
import eu.smartsocietyproject.peermanager.query.PeerQuery;
import eu.smartsocietyproject.peermanager.query.QueryOperation;
import eu.smartsocietyproject.peermanager.query.QueryRule;
import eu.smartsocietyproject.pf.helper.InternalPeerManager;
import eu.smartsocietyproject.pf.helper.PeerIntermediary;
import eu.smartsocietyproject.smartcom.PeerChannelAddressAdapter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.mail.Address;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.InternetAddress;
import javax.mail.search.SubjectTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Svetoslav Videnov <s.videnov@dsg.tuwien.ac.at>
 */
public class EmailInputAdapterWithPeerSender implements InputPullAdapter {
    private static final Logger log = LoggerFactory.getLogger(EmailInputAdapter.class);

    private final String subject;
    private final String host;
    private final String username;
    private final String password;
    private final int port;
    private final boolean authentication;
    private final String type;
    private final String subtype;
    private final boolean deleteMessage;
    private final InternalPeerManager pm;

    public EmailInputAdapterWithPeerSender(String subject, String host, 
            String username, String password, int port, boolean authentication, 
            String type, String subtype, boolean deleteMessage,
            InternalPeerManager pm) {
        this.subject = subject;
        this.host = host;
        this.username = username;
        this.password = password;
        this.port = port;
        this.authentication = authentication;
        this.type = type;
        this.subtype = subtype;
        this.deleteMessage = deleteMessage;
        this.pm = pm;
    }

    @Override
    public Message pull() throws AdapterException {
        Folder folder = null;
        try {
            folder = MailUtils.getMail(subject, host, username, password, port, authentication);

            javax.mail.Message mail = null;

            javax.mail.Message[] search = folder.search(new SubjectTerm(subject));
            if (search.length > 0) {
                mail = search[0];
            } else {
                return null;
            }

            if (deleteMessage) {
                mail.setFlag(Flags.Flag.DELETED, true);
            }
            
            List<PeerIntermediary> peers = pm.findPeers(PeerQuery.create()
                    .withRule(QueryRule.create("deliveryAddress")
                            .withOperation(QueryOperation.equals)
                            .withValue(PeerChannelAddressAdapter
                                    .convert(new PeerChannelAddress(
                                            Identifier.peer("expert"), 
                                            Identifier.channelType("Email"), 
                                            Arrays.stream(mail.getFrom())
                                                .map(ad -> ((InternetAddress) ad)
                                                        .getAddress())
                                                .collect(Collectors.toList()))
                                    )
                            )
                    )
            );

            String conversationId = mail.getSubject();
            if (conversationId.toLowerCase().startsWith("re: ")) {
                conversationId = conversationId.substring(4);
            }

            String content = null;
            try {
                content = getText(mail);
            } catch (MessagingException | IOException ignored) {}

            return new Message.MessageBuilder()
                    .setConversationId(conversationId)
                    .setSenderId(Identifier.peer(peers.get(0).getPeerId()))
                    .setType(type)
                    .setSubtype(subtype)
                    .setContent(content)
                    .create();
        } catch (Exception e) {
            throw new AdapterException(e);
        } finally {
            if (folder != null) {
                try {
                    MailUtils.close(folder);
                } catch (MessagingException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Return the primary text content of the message.
     */
    private static String getText(Part p) throws MessagingException, IOException {
        if (p.isMimeType("text/*")) {
            return (String)p.getContent();
        }

        if (p.isMimeType("multipart/alternative")) {
            // prefer html text over plain text
            Multipart mp = (Multipart)p.getContent();
            String text = null;
            for (int i = 0; i < mp.getCount(); i++) {
                Part bp = mp.getBodyPart(i);
                if (bp.isMimeType("text/plain")) {
                    if (text == null)
                        text = getText(bp);
                    continue;
                } else if (bp.isMimeType("text/html")) {
                    String s = getText(bp);
                    if (s != null)
                        return s;
                } else {
                    return getText(bp);
                }
            }
            return text;
        } else if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart)p.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                String s = getText(mp.getBodyPart(i));
                if (s != null)
                    return s;
            }
        }

        return null;
    }
}
