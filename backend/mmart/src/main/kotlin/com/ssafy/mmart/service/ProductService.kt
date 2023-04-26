package com.ssafy.mmart.service

import com.querydsl.jpa.impl.JPAQueryFactory
import com.ssafy.mmart.domain.item.Item
import com.ssafy.mmart.domain.item.QProduct.product
import com.ssafy.mmart.repository.ProductRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class ProductService @Autowired constructor(
    val productRepository : ProductRepository,
    val jpaQueryFactory: JPAQueryFactory,
) {
    //유저의 구매내역에 따라 검색 부분 리스트는 수정해야함(부가)
    fun getItem(productIdx: Int, userIdx: Int): Item? {
        return jpaQueryFactory
            .selectFrom(product)
            .where(product.productIdx.eq(productIdx))
            .fetchOne()
    }

    fun getItemByBarcode(barcode: String): Item? {
        return jpaQueryFactory
            .selectFrom(product)
            .where(product.barcode.eq(barcode))
            .fetchOne()
    }

    fun getItemByCategory(userIdx: Int, categoryIdx: Int): List<Item?> {
        return jpaQueryFactory
            .selectFrom(product)
            .where(product.category.categoryIdx.eq(categoryIdx))
            .fetch()
    }
}