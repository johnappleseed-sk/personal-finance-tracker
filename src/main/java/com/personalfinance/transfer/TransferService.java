package com.personalfinance.transfer;

import java.util.List;

import com.personalfinance.account.Account;
import com.personalfinance.account.AccountRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Manages same-currency transfers between two accounts owned by the authenticated user. */
@Service
@Validated
@Transactional(readOnly = true)
public class TransferService {

	private final TransferRepository transferRepository;
	private final AccountRepository accountRepository;

	public TransferService(TransferRepository transferRepository, AccountRepository accountRepository) {
		this.transferRepository = transferRepository;
		this.accountRepository = accountRepository;
	}

	public List<TransferView> findTransfersForUser(@NotNull @Positive Long userId) {
		return transferRepository.findAllByUserIdOrderByTransferDateDescIdDesc(userId).stream()
				.map(this::toView).toList();
	}

	public TransferView findTransferForUser(@NotNull @Positive Long userId, @NotNull @Positive Long transferId) {
		return toView(transferRepository.findByIdAndUserId(transferId, userId)
				.orElseThrow(TransferNotFoundException::new));
	}

	@Transactional
	public TransferView createTransfer(@NotNull @Positive Long userId, @NotNull @Valid TransferForm form) {
		if (form.getSourceAccountId().equals(form.getTargetAccountId())) {
			throw new TransferInputException("targetAccountId", "Choose two different accounts for a transfer.");
		}
		// Lock in deterministic ID order to avoid deadlocks
		Long firstId = Math.min(form.getSourceAccountId(), form.getTargetAccountId());
		Long secondId = Math.max(form.getSourceAccountId(), form.getTargetAccountId());
		Account firstLocked = accountRepository.findOwnedForUpdate(firstId, userId).orElse(null);
		Account secondLocked = accountRepository.findOwnedForUpdate(secondId, userId).orElse(null);

		Account source = form.getSourceAccountId().equals(firstId) ? firstLocked : secondLocked;
		Account target = form.getTargetAccountId().equals(firstId) ? firstLocked : secondLocked;

		if (source == null) {
			throw new TransferInputException("sourceAccountId", "Choose an available source account.");
		}
		if (target == null) {
			throw new TransferInputException("targetAccountId", "Choose an available target account.");
		}
		if (source.getCurrency() != target.getCurrency()) {
			throw new TransferInputException("targetAccountId",
					"Both accounts must use the same currency (" + source.getCurrency() + ").");
		}
		return toView(transferRepository.saveAndFlush(new AccountTransfer(userId, source, target, form)));
	}

	@Transactional
	public void deleteTransfer(@NotNull @Positive Long userId, @NotNull @Positive Long transferId) {
		AccountTransfer transfer = transferRepository.findOwnedForUpdate(transferId, userId)
				.orElseThrow(TransferNotFoundException::new);
		transferRepository.delete(transfer);
		transferRepository.flush();
	}

	private TransferView toView(AccountTransfer transfer) {
		return new TransferView(
				transfer.getId(),
				transfer.getSourceAccount().getId(),
				transfer.getSourceAccount().getName(),
				transfer.getTargetAccount().getId(),
				transfer.getTargetAccount().getName(),
				transfer.getAmount(),
				transfer.getCurrency(),
				transfer.getTransferDate(),
				transfer.getDescription()
		);
	}
}
