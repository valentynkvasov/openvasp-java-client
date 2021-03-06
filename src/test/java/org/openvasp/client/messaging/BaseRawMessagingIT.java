package org.openvasp.client.messaging;

import com.google.common.collect.ImmutableList;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.openvasp.client.VaspInstance;
import org.openvasp.client.config.VaspModule;
import org.openvasp.client.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openvasp.client.common.Json.loadTestJson;
import static org.openvasp.client.common.TestConstants.*;

/**
 * @author Olexandr_Bilovol@epam.com
 */
public abstract class BaseRawMessagingIT {

    private final Logger log = LoggerFactory.getLogger(getClass());

    final List<VaspMessage> messages;
    final VaspModule module1, module2;

    VaspInstance instance1, instance2;

    public BaseRawMessagingIT(@NonNull final VaspModule module1, @NonNull final VaspModule module2) {
        this.module1 = module1;
        this.module2 = module2;

        messages = ImmutableList.of(
                loadTestJson(SessionRequest.class, "raw-messaging/message-1.json"),
                loadTestJson(SessionReply.class, "raw-messaging/message-2.json"),
                loadTestJson(TransferRequest.class, "raw-messaging/message-3.json"),
                loadTestJson(TransferReply.class, "raw-messaging/message-4.json"),
                loadTestJson(TransferDispatch.class, "raw-messaging/message-5.json"),
                loadTestJson(TransferConfirmation.class, "raw-messaging/message-6.json"),
                loadTestJson(TerminationMessage.class, "raw-messaging/message-7.json")
        );

        for (int i = 0; i < messages.size(); i++) {
            val header = messages.get(i).getHeader();
            header.setMessageId(Numeric.toHexStringNoPrefixZeroPadded(BigInteger.valueOf(i), VaspMessage.Header.MSG_ID_LENGTH));
        }
    }

    @BeforeEach
    public void setUp() {
        this.instance1 = new VaspInstance(module1, false);
        this.instance1.addVaspIdResolver(message -> Optional.of(CONTRACT_ADDRESS_2));
        this.instance2 = new VaspInstance(module2, false);
    }

    @AfterEach
    public void tearDown() {
        instance1.close();
        instance1 = null;
        instance2.close();
        instance2 = null;
    }

    @SneakyThrows
    public void checkAsymmetricSendAndReceive() {
        log.debug("check asymmetric sending and receiving");

        val receivedMessages = new ArrayList<VaspMessage>();

        instance1.addTopicListener(
                module1.getVaspCode().toTopic(),
                EncryptionType.ASSYMETRIC,
                module1.getVaspConfig().getHandshakePrivateKey(),
                topicEvent -> {
                    val message = topicEvent.getPayload();
                    logVaspMessage(message);
                    receivedMessages.add(message);
                    if (receivedMessages.size() == messages.size()) {
                        instance1.shutdown();
                    }
                }
        );

        val contract1 = instance2.getVaspContractInfo(CONTRACT_ADDRESS_1);
        for (val message : messages) {
            instance2.send(
                    module1.getVaspCode().toTopic(),
                    EncryptionType.ASSYMETRIC,
                    contract1.getHandshakeKey(),
                    message);
        }

        if (!instance1.waitForTermination(WAIT_TIMEOUT_1)) {
            instance1.shutdown();
            instance1.waitForTermination(WAIT_TIMEOUT_2);
        }

        receivedMessages.sort(Comparator.comparing(message -> message.getHeader().getMessageId()));
        assertThat(receivedMessages)
                .usingRecursiveFieldByFieldElementComparator()
                .isEqualTo(messages);
    }

    @SneakyThrows
    public void checkSymmetricSendAndReceive() {
        log.debug("check symmetric sending and receiving");

        val keyId = instance1.generateSymKeyFromPassword("Hello,World!");
        val symKey = instance1.getSymKey(keyId);

        val receivedMessages = new ArrayList<VaspMessage>();

        instance1.addTopicListener(
                module1.getVaspCode().toTopic(),
                EncryptionType.SYMMETRIC,
                symKey,
                topicEvent -> {
                    val message = topicEvent.getPayload();
                    logVaspMessage(message);
                    receivedMessages.add(message);
                    if (receivedMessages.size() == messages.size()) {
                        instance1.shutdown();
                    }
                }
        );

        for (val message : messages) {
            instance2.send(
                    module1.getVaspCode().toTopic(),
                    EncryptionType.SYMMETRIC,
                    symKey,
                    message);
        }

        if (!instance1.waitForTermination(WAIT_TIMEOUT_1)) {
            instance1.shutdown();
            instance1.waitForTermination(WAIT_TIMEOUT_2);
        }

        receivedMessages.sort(Comparator.comparing(message -> message.getHeader().getMessageId()));
        assertThat(receivedMessages)
                .usingRecursiveFieldByFieldElementComparator()
                .isEqualTo(messages);
    }

    private void logVaspMessage(final VaspMessage vaspMessage) {
        if (log.isDebugEnabled()) {
            log.debug("{}(type={}, msgid={}, comment='{}')",
                    vaspMessage.getClass().getSimpleName(),
                    vaspMessage.getHeader().getMessageType().id,
                    vaspMessage.getHeader().getMessageId(),
                    vaspMessage.getComment());
        }
    }

}
