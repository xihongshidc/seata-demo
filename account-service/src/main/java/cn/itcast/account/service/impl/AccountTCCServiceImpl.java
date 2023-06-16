package cn.itcast.account.service.impl;

import cn.itcast.account.entity.AccountFreeze;
import cn.itcast.account.mapper.AccountFreezeMapper;
import cn.itcast.account.mapper.AccountMapper;
import cn.itcast.account.service.AccountTCCService;
import io.seata.core.context.RootContext;
import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;

@Service
public class AccountTCCServiceImpl implements AccountTCCService {
    @Autowired
    private AccountMapper accountMapper;
    @Autowired
    private AccountFreezeMapper accountFreezeMapper;

    /**
     * TRY
     *
     * @param userId
     * @param money
     */
    @Transactional
    @Override
    public void deduct(String userId, int money) {
        //获取事务id  全局事务id
        String xid = RootContext.getXID();
        //判断freeze中是否有 冻结记录,如果有哦,一定Cancel执行过,
        AccountFreeze accountFreeze1 = accountFreezeMapper.selectById(xid);
        if (accountFreeze1 != null) {
            return;
        }
        //执行sql完成对数据库的操作
        accountMapper.deduct(userId, money);
        //保存AccountFreeze  记录冻结 金额
        AccountFreeze accountFreeze = new AccountFreeze();
        accountFreeze.setFreezeMoney(money);
        accountFreeze.setState(AccountFreeze.State.TRY);
        accountFreeze.setUserId(userId);
        accountFreeze.setXid(xid);
        int insert = accountFreezeMapper.insert(accountFreeze);


    }

    /**
     * 提交
     *
     * @param ctx
     * @return
     */
    @Override
    public boolean confirm(BusinessActionContext ctx) {
        //删除冻结表
        String xid = ctx.getXid();
        int i = accountFreezeMapper.deleteById(xid);
        return i == 1;
    }

    /**
     * \
     * 回滚
     *
     * @param ctx
     * @return
     */
    @Override
    public boolean cancel(BusinessActionContext ctx) {
//        return true;
        String xid = ctx.getXid();
        String userId = ctx.getActionContext("userId").toString();
        AccountFreeze accountFreeze = accountFreezeMapper.selectById(xid);
//        Integer freezeMoney = accountFreeze.getFreezeMoney();
//        String userId = accountFreeze.getUserId();
        //空回滚 判断!
        if (accountFreeze == null) {
            //空回滚也需要有记录
            accountFreeze = new AccountFreeze();
            accountFreeze.setFreezeMoney(0);
            accountFreeze.setState(AccountFreeze.State.CANCEL);
            accountFreeze.setUserId(userId);
            accountFreeze.setXid(xid);
            int insert = accountFreezeMapper.insert(accountFreeze);
            return true;
        }

        //判断幂等性
        if (accountFreeze.getState() == AccountFreeze.State.CANCEL) {
            //已经处理过一次 cancel
            return true;
        }

        //恢复数据库 金钱余额
        accountMapper.refund(accountFreeze.getUserId(), accountFreeze.getFreezeMoney());
        // 更新冻结余额 状态 和金额
        accountFreeze.setFreezeMoney(0);
        accountFreeze.setState(AccountFreeze.State.CANCEL);
        int i = accountFreezeMapper.updateById(accountFreeze);
        return i == 1;
    }
}
