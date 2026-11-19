package vn.vshop.modules.identity.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.modules.identity.entity.AccountStatus;
import vn.vshop.modules.identity.repository.AccountRepository;

@Service
public class AccountService {
    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public UUID requireActiveAccount(String subject) {
        var account = accounts.findOrCreate(subject);
        if (account.status() != AccountStatus.ACTIVE) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_SUSPENDED,
                    "Tài khoản hiện không thể thực hiện thao tác này.");
        }
        return account.id();
    }
}
