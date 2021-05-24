package com.nithin.Server.function.repository;

import in.nmaloth.entity.product.ProductId;
import in.nmaloth.entity.product.ProductLimitsDef;
import org.springframework.data.repository.CrudRepository;

public interface ProductLimitRepository extends CrudRepository<ProductLimitsDef, ProductId> {
}
