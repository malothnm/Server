package com.nithin.Server.function.repository;

import in.nmaloth.entity.product.ProductDef;
import in.nmaloth.entity.product.ProductId;
import org.springframework.data.repository.CrudRepository;

public interface ProductRepository extends CrudRepository<ProductDef, ProductId> {
}
