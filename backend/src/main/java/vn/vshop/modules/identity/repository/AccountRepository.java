package vn.vshop.modules.identity.repository;

import vn.vshop.modules.identity.entity.Account;

public interface AccountRepository {
    Account findOrCreate(String externalSubject);
}
