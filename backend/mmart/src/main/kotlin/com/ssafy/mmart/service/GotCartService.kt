package com.ssafy.mmart.service

import com.ssafy.mmart.domain.getCart.dto.GetCartItem
import com.ssafy.mmart.domain.gotCart.dto.GotCartItem
import com.ssafy.mmart.domain.gotCart.dto.GotCartReq
import com.ssafy.mmart.domain.gotCart.dto.GotCartRes
import com.ssafy.mmart.exception.conflict.GotCartEmptyException
import com.ssafy.mmart.exception.not_found.*
import com.ssafy.mmart.repository.ItemCouponRepository
import com.ssafy.mmart.repository.ItemItemCouponRepository
import com.ssafy.mmart.repository.ItemRepository
import com.ssafy.mmart.repository.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class GotCartService @Autowired constructor(
    private var redisTemplate: RedisTemplate<String, Any>,
    val userRepository: UserRepository,
    val itemRepository: ItemRepository,
    val itemCouponRepository: ItemCouponRepository,
    val itemItemCouponRepository: ItemItemCouponRepository,
){
    val getCart = "GOTCART"
    val gotCartOps: HashOperations<String, Int, MutableMap<Int, Int>> = redisTemplate.opsForHash()


    fun setGotCarts(temp: MutableMap<Int, Int>): GotCartRes {
        var total = 0
        val gotCartRes = GotCartRes(mutableListOf(), total)
        temp.keys.forEach{ hashKey ->
            val tempQuantity = temp[hashKey]!!
            val tempItem = itemRepository.findByIdOrNull(hashKey) ?: throw ItemNotFoundException()
            var tempPrice = tempItem.price
            var isCoupon = false;
            val tempCoupon = itemItemCouponRepository.findByItem_ItemIdx(hashKey)
            if (tempCoupon != null) {
                isCoupon=true
                tempPrice -= itemCouponRepository.findByIdOrNull(tempCoupon.itemCoupon.itemCouponIdx)!!.couponDiscount
            }
            gotCartRes.itemList.add(
                GotCartItem(
                    hashKey,
                    tempItem.itemName,
                    tempItem.price,
                    tempItem.thumbnail!!,
                    isCoupon,
                    tempPrice,
                    tempQuantity
                )
            )
            total += tempPrice * tempQuantity
        }
        gotCartRes.total = total
        return gotCartRes
    }

    fun getGotCarts(userIdx: Int): GotCartRes {
        userRepository.findByIdOrNull(userIdx) ?: throw UserNotFoundException()
        val temp = gotCartOps.get(getCart, userIdx) ?: throw GotCartEmptyException()
        return setGotCarts(temp)
    }

    fun createGotCart(gotCartReq: GotCartReq): GotCartRes {
        userRepository.findByIdOrNull(gotCartReq.userIdx) ?: throw UserNotFoundException()
        itemRepository.findByIdOrNull(gotCartReq.itemIdx) ?: throw ItemNotFoundException()

        var temp = gotCartOps.get(getCart, gotCartReq.userIdx)
        if (temp.isNullOrEmpty()) {
            temp = mutableMapOf()
            temp[gotCartReq.itemIdx] = gotCartReq.quantity
            gotCartOps.put(getCart, gotCartReq.userIdx, temp)
        } else {
            if (temp.containsKey(gotCartReq.itemIdx)) {
                temp[gotCartReq.itemIdx] = temp[gotCartReq.itemIdx]!! + gotCartReq.quantity
            } else {
                temp[gotCartReq.itemIdx] = gotCartReq.quantity
            }
            gotCartOps.put(getCart, gotCartReq.userIdx, temp)
        }
        return setGotCarts(temp)
    }

    fun updateGotCart(gotCartReq: GotCartReq): GotCartRes {
        userRepository.findByIdOrNull(gotCartReq.userIdx) ?: throw UserNotFoundException()
        val item = itemRepository.findByIdOrNull(gotCartReq.itemIdx) ?: throw ItemNotFoundException()

        if (gotCartReq.quantity <= 0 ) throw WrongQuantityException()
        if (gotCartReq.quantity > item.inventory) throw OverQuantityException()

        val temp = gotCartOps.get(getCart, gotCartReq.userIdx)
        if (temp.isNullOrEmpty()) {
            throw GotCartEmptyException()
        } else {
            if (temp.containsKey(gotCartReq.itemIdx)) {
                temp[gotCartReq.itemIdx] = gotCartReq.quantity
            } else {
                throw GotCartNotFoundException()
            }
            gotCartOps.put(getCart, gotCartReq.userIdx, temp)
        }
        return setGotCarts(temp)
    }

    fun deleteGotCart(userIdx: Int, itemIdx: Int): GotCartRes {
        userRepository.findByIdOrNull(userIdx) ?: throw UserNotFoundException()
        itemRepository.findByIdOrNull(itemIdx) ?: throw ItemNotFoundException()
        val temp = gotCartOps.get(getCart, userIdx)
        if (temp.isNullOrEmpty()) throw GotCartEmptyException()
        if (temp.containsKey(itemIdx)) {
            temp.remove(itemIdx)
        } else {
            throw GotCartNotFoundException()
        }
        gotCartOps.put(getCart, userIdx, temp)
        return setGotCarts(temp)
    }
}