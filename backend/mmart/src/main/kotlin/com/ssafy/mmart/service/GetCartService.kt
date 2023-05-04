package com.ssafy.mmart.service

import com.ssafy.mmart.domain.getCart.dto.CreateGetCartReq
import com.ssafy.mmart.domain.getCart.dto.GetCartItem
import com.ssafy.mmart.domain.getCart.dto.GetCartRes
import com.ssafy.mmart.domain.getCart.dto.PutGetCartReq
import com.ssafy.mmart.exception.bad_request.BadAccessException
import com.ssafy.mmart.exception.conflict.GetCartEmptyException
import com.ssafy.mmart.exception.not_found.*
import com.ssafy.mmart.repository.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

@Service
class GetCartService @Autowired constructor(
    private var redisTemplate: RedisTemplate<String, Any>,
    var userRepository: UserRepository,
    var itemRepository: ItemRepository,
    var categoryRepository: CategoryRepository,
    var itemItemCouponRepository: ItemItemCouponRepository,
    var couponRepository: ItemCouponRepository,
) {
    //카테고리는 인덱스를 마이너스로, 인벤토리는 0으로 쓰기
    val getCart = "GETCART"
    val getCartOps: HashOperations<String, Int, MutableMap<Int, Int>> = redisTemplate.opsForHash()

    fun createGetCart(createGetCartReq: CreateGetCartReq): GetCartRes {
        //유저가 존재하는지 확인
        userRepository.findById(createGetCartReq.userIdx).orElseThrow(::UserNotFoundException)
        var temp = getCartOps.get(getCart, createGetCartReq.userIdx)

        //선택한 수량이 0 초과인지 체크
        if (createGetCartReq.quality <= 0)
            throw WrongQuantityException()

        //아이템 존재하는지 확인
        val item = itemRepository.findById(createGetCartReq.itemIdx).orElseThrow(::ItemNotFoundException)

        //내가 담기를 원하는 재고가 기존의 수량을 넘는지 체크
        if (item.inventory < createGetCartReq.quality)
            throw OverQuantityException()
//        if (createGetCartReq.itemIdx < 0) {
//            //카테고리 추가일 경우
//            //카테고리가 존재하는지 확인해야함!!
//            categoryRepository.findById(-createGetCartReq.itemIdx).orElseThrow(::ItemNotFoundException)
//
//            if (temp == null) {
//                var map: MutableMap<Int, Int> = mutableMapOf()
//                map.put(createGetCartReq.itemIdx, 0)
//                getCartOps.put(GETCART, createGetCartReq.userIdx, map)
//            } else {
//                //MAP에 내가 넣으려는 값이 있는지 체크
//                val flag = temp.containsKey(createGetCartReq.itemIdx)
//                if (!flag) {//값이 없으면
//                    temp.put(createGetCartReq.itemIdx, 0)
//                }
//                getCartOps.put(GETCART, createGetCartReq.userIdx, temp)
//            }
//        }

        if (temp == null) {
            temp = mutableMapOf()
            temp[createGetCartReq.itemIdx] = createGetCartReq.quality
            getCartOps.put(getCart, createGetCartReq.userIdx, temp)
        } else {
            //MAP에 내가 넣으려는 값이 있는지 체크
            val flag = temp.containsKey(createGetCartReq.itemIdx)
            if (flag) {//값이 있으면
                temp[createGetCartReq.itemIdx] = temp[createGetCartReq.itemIdx]!! + createGetCartReq.quality
            } else {//값이 없으면
                temp[createGetCartReq.itemIdx] = createGetCartReq.quality
            }
            getCartOps.put(getCart, createGetCartReq.userIdx, temp)
        }
        return setGetCarts(temp)
    }

    fun putGetCart(putGetCartReq: PutGetCartReq): GetCartRes {
        //유저가 존재하는지 확인
        if (putGetCartReq.itemIdx < 0)
            throw BadAccessException()
        userRepository.findById(putGetCartReq.userIdx).orElseThrow(::UserNotFoundException)
        //아이템 존재하는지 확인
        val item = itemRepository.findById(putGetCartReq.itemIdx).orElseThrow(::ItemNotFoundException)

        //선택한 수량이 0 초과인지 체크
        if (putGetCartReq.quality <= 0)
            throw WrongQuantityException()

        //내가 담기를 원하는 재고가 기존의 수량을 넘는지 체크
        if (item.inventory < putGetCartReq.quality)
            throw OverQuantityException()

        val temp = getCartOps.get(getCart, putGetCartReq.userIdx)
        if (temp.isNullOrEmpty()) {
            throw GetCartEmptyException()
        } else {
            //MAP에 내가 넣으려는 값이 있는지 체크
            val flag = temp.containsKey(putGetCartReq.itemIdx)
            if (flag) {//값이 있으면(있어야함. 수량 수정이므로)
                temp[putGetCartReq.itemIdx] = putGetCartReq.quality
            } else {//값이 없으면 익셉션 발생
                throw GetCartNotFoundException()
            }
            getCartOps.put(getCart, putGetCartReq.userIdx, temp)
        }
        return setGetCarts(temp)
    }

    fun getGetCart(userIdx: Int): GetCartRes {
        //유저가 존재하는지 확인
        userRepository.findById(userIdx).orElseThrow(::UserNotFoundException)

        val temp = getCartOps.get(getCart, userIdx)
        if (temp.isNullOrEmpty()) {
            throw GetCartEmptyException()
        } else {
            return setGetCarts(temp)
        }
    }

    fun deleteGetCarts(userIdx: Int): GetCartRes {
        //유저가 존재하는지 확인
        userRepository.findById(userIdx).orElseThrow(::UserNotFoundException)
        val temp = getCartOps.get(getCart, userIdx)
        if (!temp.isNullOrEmpty()) {
            temp.clear()
            getCartOps.put(getCart, userIdx, temp)
        }
        return setGetCarts(temp!!)
    }

    fun deleteGetCart(userIdx: Int, itemIdx: Int): GetCartRes {
        //유저가 존재하는지 확인
        userRepository.findById(userIdx).orElseThrow(::UserNotFoundException)
        val temp = getCartOps.get(getCart, userIdx)
        if (temp.isNullOrEmpty())
            throw GetCartEmptyException()
        if (itemIdx < 0) {
            //카테고리인 경우
            //카테고리가 존재하는지 확인
            categoryRepository.findById(-itemIdx).orElseThrow(::ItemNotFoundException)

        } else {
            //아이템인 경우
            //아이템 존재하는지 확인
            itemRepository.findById(itemIdx).orElseThrow(::ItemNotFoundException)
        }
        //MAP에 내가 삭제하려는 값이 있는지 체크
        val flag = temp.containsKey(itemIdx)
        if (flag) {//값이 있으면(있어야함. 삭제이므로)
            temp.remove(itemIdx)
        } else {//값이 없으면 익셉션 발생
            throw GetCartNotFoundException()
        }
        getCartOps.put(getCart, userIdx, temp)

        return setGetCarts(temp)
    }

    fun setGetCarts(temp: MutableMap<Int, Int>): GetCartRes {
        val getCartRes = GetCartRes(mutableListOf(), 0)
        if (temp.isNotEmpty()) {
            temp.keys.forEach { haskKey ->
                val item = itemRepository.findById(haskKey).orElseThrow(::ItemNotFoundException)
                var eachPrice = item.price
                var isCoupon = false;
                //쿠폰이 있으면, 쿠폰 가격만큼 item의 price에서 빼준다.
                val itemItemCoupon = itemItemCouponRepository.findByItem_ItemIdx(item.itemIdx!!)
                if (itemItemCoupon != null) {
                    isCoupon = true;
                    val itemCoupon = couponRepository.findById(itemItemCoupon.itemCoupon.itemCouponIdx!!)
                    eachPrice -= itemCoupon.get().couponDiscount
                }
                getCartRes.itemList.add(
                    GetCartItem(
                        haskKey,
                        item.itemName,
                        item.price,
                        item.thumbnail!!,
                        isCoupon,
                        eachPrice,
                        temp[haskKey]!!
                    )
                )
                getCartRes.total += eachPrice * temp[haskKey]!!
            }
        }
        return getCartRes

    }

}
