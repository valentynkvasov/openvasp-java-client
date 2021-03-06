package org.openvasp.transfer.account;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.openvasp.client.SimpleTransferHandler;
import org.openvasp.client.model.*;
import org.openvasp.client.session.Session;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * @author Olexandr_Bilovol@epam.com
 */
@Slf4j
final class AccountTransferHandler implements SimpleTransferHandler {

    private final AccountService accountService;

    public AccountTransferHandler(@NonNull final AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public void accept(@NonNull final VaspMessage message, @NonNull final Session session) {
        logMessage(session.vaspInfo().getVaspCode(), message);
        SimpleTransferHandler.super.accept(message, session);
    }

    @Override
    public void onTransferRequest(
            @NonNull final TransferRequest request,
            @NonNull final TransferReply response,
            @NonNull final Session session) {

        final Vaan beneficiaryVaan = request.getBeneficiary().getVaan();
        final String beneficiaryAccount = accountService.getAccount(beneficiaryVaan);
        response.setDestinationAddress(beneficiaryAccount);

        SimpleTransferHandler.super.onTransferRequest(request, response, session);
    }

    @Override
    public void onTransferReply(
            @NonNull final TransferReply request,
            @NonNull final TransferDispatch response,
            @NonNull final Session session) {

        final TransferInfo transferInfo = session.transferInfo();
        final Vaan originatorVaan = transferInfo.getOriginator().getVaan();
        final BigDecimal amount = transferInfo.getTransfer().getAmount();
        final String originatorAccount = accountService.getAccount(originatorVaan);
        accountService.subtract(originatorAccount, amount);

        final String beneficiaryAccount = request.getDestinationAddress();
        final String txID = accountService.add(beneficiaryAccount, amount);

        final TransferDispatch.Tx tx = new TransferDispatch.Tx();
        tx.setId(txID);
        tx.setDateTime(ZonedDateTime.now());
        tx.setSendingAddress(request.getDestinationAddress());
        response.setTx(tx);

        SimpleTransferHandler.super.onTransferReply(request, response, session);
    }

    @Override
    public void onTransferDispatch(
            @NonNull final TransferDispatch request,
            @NonNull final TransferConfirmation response,
            @NonNull final Session session) {

        final TransferInfo transferInfo = session.transferInfo();
        final BigDecimal amount = transferInfo.getTransfer().getAmount();
        final TransferDispatch.Tx tx = request.getTx();
        if (accountService.checkTransaction(tx.getId(), amount)) {
            response.getHeader().setResponseCode(VaspResponseCode.OK.id);
        } else {
            response.getHeader().setResponseCode(VaspResponseCode.TC_ASSETS_NOT_RECEIVED.id);
        }

        SimpleTransferHandler.super.onTransferDispatch(request, response, session);
    }

    private void logMessage(@NonNull final VaspCode vaspCode, @NonNull final VaspMessage vaspMessage) {
        log.debug("process {} at {}", vaspMessage.getClass().getSimpleName(), vaspCode);
    }

}
