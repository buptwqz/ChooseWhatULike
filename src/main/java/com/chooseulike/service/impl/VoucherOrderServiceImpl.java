package com.chooseulike.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chooseulike.dto.Result;
import com.chooseulike.entity.SeckillVoucher;
import com.chooseulike.entity.VoucherOrder;
import com.chooseulike.mapper.VoucherOrderMapper;
import com.chooseulike.service.ISeckillVoucherService;
import com.chooseulike.service.IVoucherOrderService;
import com.chooseulike.utils.RedisIdWorker;
import com.chooseulike.utils.UserHolder;
import org.apache.tomcat.jni.Local;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 查询优惠券后判断
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始！");
        }

        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束！");
        }

        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            // 获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }
    @Transactional
    public Result createVoucherOrder(Long voucherId){

        // 一人一单

        Long userId = UserHolder.getUser().getId();


            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();

            if (count > 0) {
                return Result.fail("已经购买一次");
            }

            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId).gt("stock", 0)
                    .update();

            if (!success) {
                return Result.fail("扣减失败，库存不足");
            }


            // 创建订单
            VoucherOrder voucherOrder = new VoucherOrder();

            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);

            voucherOrder.setUserId(userId);

            voucherOrder.setVoucherId(voucherId);

            save(voucherOrder);
            return Result.ok(orderId);
        }
}


