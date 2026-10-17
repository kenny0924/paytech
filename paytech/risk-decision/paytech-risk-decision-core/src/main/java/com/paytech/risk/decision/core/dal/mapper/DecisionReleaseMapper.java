package com.paytech.risk.decision.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.risk.decision.core.dal.dataobject.DecisionRelease;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;

/** Decision Release 根的 Mapper。 */
public interface DecisionReleaseMapper {

    DecisionRelease selectRelease(@Param("releaseId") long releaseId);

    DecisionRelease lockRelease(@Param("releaseId") long releaseId);

    List<DecisionRelease> selectReleases(
        @Param("pointCode") String pointCode,
        @Param("status") DecisionReleaseStatus status,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    long countReleases(
        @Param("pointCode") String pointCode,
        @Param("status") DecisionReleaseStatus status
    );

    long countNonCanonicalReleaseNumbers();

    long selectMaxReleaseNo();

    int insertRelease(@Param("value") DecisionRelease value);

    int moveReleaseToValidating(
        @Param("releaseId") long releaseId,
        @Param("expectedStatus") DecisionReleaseStatus expectedStatus,
        @Param("expectedVersion") int expectedVersion,
        @Param("updateTime") Instant updateTime
    );

    int moveReleaseToRejected(
        @Param("releaseId") long releaseId,
        @Param("expectedStatus") DecisionReleaseStatus expectedStatus,
        @Param("expectedVersion") int expectedVersion,
        @Param("updateTime") Instant updateTime
    );
}
