package com.paytech.platform.support.referencedata.service.processor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.referencedata.domainservice.repository.CountrySubdivisionRepository;
import com.paytech.platform.support.referencedata.service.processor.request.ListCountrySubdivisionsRequest;
import com.paytech.platform.support.referencedata.service.processor.response.CountrySubdivisionResult;

import lombok.AllArgsConstructor;

/** 查询行政区；只读，不开事务。 */
@Component
@AllArgsConstructor
public class ListCountrySubdivisionsProcessor
    extends AbstractProcessor<ListCountrySubdivisionsRequest, List<CountrySubdivisionResult>> {

    private final CountrySubdivisionRepository repository;

    @Override
    protected List<CountrySubdivisionResult> processRequest(ListCountrySubdivisionsRequest request) {
        return repository.find(request.getCountryCode(), request.getStatus(), request.getCodes()).stream()
            .map(CountrySubdivisionResult::of).toList();
    }
}
