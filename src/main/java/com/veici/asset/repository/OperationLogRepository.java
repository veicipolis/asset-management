package com.veici.asset.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OperationLogRepository {

    private final EntityManager entityManager;

    public void recordAssetCreated(
            Long actorId, Long assetId, String assetCode, String name) {

        entityManager.createNativeQuery("""
                INSERT INTO operation_log
                    (actor_id, log_category, action,
                     target_type, target_id, result, details)
                VALUES
                    (:actorId, 'BUSINESS', 'ASSET_CREATE',
                     'ASSET', :assetId, 'SUCCESS',
                     JSON_OBJECT('assetCode', :assetCode, 'name', :name))
                """)
                .setParameter("actorId", actorId)
                .setParameter("assetId", assetId)
                .setParameter("assetCode", assetCode)
                .setParameter("name", name)
                .executeUpdate();
    }

    public void recordAssetUpdated(
            Long actorId,
            Long assetId,
            String beforeJson,
            String afterJson) {

        entityManager.createNativeQuery("""
            INSERT INTO operation_log
                (actor_id, log_category, action,
                 target_type, target_id, result, details)
            VALUES
                (:actorId, 'BUSINESS', 'ASSET_UPDATE',
                 'ASSET', :assetId, 'SUCCESS',
                 JSON_OBJECT(
                     'before', CAST(:beforeJson AS JSON),
                     'after', CAST(:afterJson AS JSON)
                 ))
            """)
                .setParameter("actorId", actorId)
                .setParameter("assetId", assetId)
                .setParameter("beforeJson", beforeJson)
                .setParameter("afterJson", afterJson)
                .executeUpdate();
    }

    public void recordApplicationCreated(
            Long actorId, Long applicationId, Long assetId, String purpose) {

        entityManager.createNativeQuery("""
            INSERT INTO operation_log
                (actor_id, log_category, action,
                 target_type, target_id, result, details)
            VALUES
                (:actorId, 'BUSINESS', 'APPLICATION_CREATE',
                 'ASSET_APPLICATION', :applicationId, 'SUCCESS',
                 JSON_OBJECT('assetId', :assetId, 'purpose', :purpose))
            """)
                .setParameter("actorId", actorId)
                .setParameter("applicationId", applicationId)
                .setParameter("assetId", assetId)
                .setParameter("purpose", purpose)
                .executeUpdate();
    }

    public void recordApplicationReviewed(
            Long actorId,
            Long applicationId,
            boolean approved,
            String comment,
            Long beforeVersion,
            Long afterVersion) {

        entityManager.createNativeQuery("""
            INSERT INTO operation_log
                (actor_id, log_category, action,
                 target_type, target_id, result, details)
            VALUES
                (:actorId, 'BUSINESS', :action,
                 'ASSET_APPLICATION', :applicationId, 'SUCCESS',
                 JSON_OBJECT(
                     'beforeStatus', 'PENDING',
                     'afterStatus', :afterStatus,
                     'comment', :comment,
                     'beforeVersion', :beforeVersion,
                     'afterVersion', :afterVersion
                 ))
            """)
                .setParameter("actorId", actorId)
                .setParameter("applicationId", applicationId)
                .setParameter("action",
                        approved ? "APPLICATION_APPROVE" : "APPLICATION_REJECT")
                .setParameter("afterStatus",
                        approved ? "APPROVED" : "REJECTED")
                .setParameter("comment", comment == null ? "" : comment)
                .setParameter("beforeVersion", beforeVersion)
                .setParameter("afterVersion", afterVersion)
                .executeUpdate();
    }

    public void recordAssetIssued(
            Long actorId,
            Long loanId,
            Long applicationId,
            Long assetId,
            Long borrowerId) {

        entityManager.createNativeQuery("""
            INSERT INTO operation_log
                (actor_id, log_category, action,
                 target_type, target_id, result, details)
            VALUES
                (:actorId, 'BUSINESS', 'ASSET_ISSUE',
                 'ASSET_LOAN', :loanId, 'SUCCESS',
                 JSON_OBJECT(
                     'applicationId', :applicationId,
                     'assetId', :assetId,
                     'borrowerId', :borrowerId,
                     'beforeAssetStatus', 'IDLE',
                     'afterAssetStatus', 'IN_USE',
                     'beforeApplicationStatus', 'APPROVED',
                     'afterApplicationStatus', 'ISSUED'
                 ))
            """)
                .setParameter("actorId", actorId)
                .setParameter("loanId", loanId)
                .setParameter("applicationId", applicationId)
                .setParameter("assetId", assetId)
                .setParameter("borrowerId", borrowerId)
                .executeUpdate();
    }

    public void recordReturnRequested(
            Long actorId,
            Long returnApplicationId,
            Long loanId,
            Long assetId) {

        entityManager.createNativeQuery("""
            INSERT INTO operation_log
                (actor_id, log_category, action,
                 target_type, target_id, result, details)
            VALUES
                (:actorId, 'BUSINESS', 'RETURN_REQUEST',
                 'RETURN_APPLICATION', :returnApplicationId, 'SUCCESS',
                 JSON_OBJECT(
                     'loanId', :loanId,
                     'assetId', :assetId,
                     'status', 'PENDING'
                 ))
            """)
                .setParameter("actorId", actorId)
                .setParameter("returnApplicationId", returnApplicationId)
                .setParameter("loanId", loanId)
                .setParameter("assetId", assetId)
                .executeUpdate();
    }

    public void recordReturnReceived(
            Long actorId,
            Long returnApplicationId,
            Long loanId,
            Long assetId) {

        entityManager.createNativeQuery("""
        INSERT INTO operation_log
            (actor_id, log_category, action,
             target_type, target_id, result, details)
        VALUES
            (:actorId, 'BUSINESS', 'RETURN_RECEIVE',
             'RETURN_APPLICATION', :returnApplicationId, 'SUCCESS',
             JSON_OBJECT(
                 'loanId', :loanId,
                 'assetId', :assetId,
                 'beforeStatus', 'PENDING',
                 'afterStatus', 'COMPLETED',
                 'beforeAssetStatus', 'IN_USE',
                 'afterAssetStatus', 'IDLE'
             ))
        """)
                .setParameter("actorId", actorId)
                .setParameter("returnApplicationId", returnApplicationId)
                .setParameter("loanId", loanId)
                .setParameter("assetId", assetId)
                .executeUpdate();
    }

    public void recordUserEnabledChanged(
            Long actorId,
            Long userId,
            boolean beforeEnabled,
            boolean afterEnabled,
            Long beforeVersion,
            Long afterVersion) {

        entityManager.createNativeQuery("""
        INSERT INTO operation_log
            (actor_id, log_category, action,
             target_type, target_id, result, details)
        VALUES
            (:actorId, 'ADMIN', 'USER_ENABLED_CHANGE',
             'APP_USER', :userId, 'SUCCESS',
             JSON_OBJECT(
                 'beforeEnabled', :beforeEnabled,
                 'afterEnabled', :afterEnabled,
                 'beforeVersion', :beforeVersion,
                 'afterVersion', :afterVersion
             ))
        """)
                .setParameter("actorId", actorId)
                .setParameter("userId", userId)
                .setParameter("beforeEnabled", beforeEnabled)
                .setParameter("afterEnabled", afterEnabled)
                .setParameter("beforeVersion", beforeVersion)
                .setParameter("afterVersion", afterVersion)
                .executeUpdate();
    }

    public void recordUserCreated(
            Long actorId,
            Long userId,
            String username,
            String displayName,
            Long departmentId) {

        entityManager.createNativeQuery("""
        INSERT INTO operation_log
            (actor_id, log_category, action,
             target_type, target_id, result, details)
        VALUES
            (:actorId, 'ADMIN', 'USER_CREATE',
             'APP_USER', :userId, 'SUCCESS',
             JSON_OBJECT(
                 'username', :username,
                 'displayName', :displayName,
                 'departmentId', :departmentId,
                 'roles', JSON_ARRAY()
             ))
        """)
                .setParameter("actorId", actorId)
                .setParameter("userId", userId)
                .setParameter("username", username)
                .setParameter("displayName", displayName)
                .setParameter("departmentId", departmentId)
                .executeUpdate();
    }

    public void recordUserProfileUpdated(
            Long actorId,
            Long userId,
            String beforeName,
            String afterName,
            Long beforeDepartmentId,
            Long afterDepartmentId,
            Long beforeVersion,
            Long afterVersion) {

        entityManager.createNativeQuery("""
        INSERT INTO operation_log
            (actor_id, log_category, action,
             target_type, target_id, result, details)
        VALUES
            (:actorId, 'ADMIN', 'USER_PROFILE_UPDATE',
             'APP_USER', :userId, 'SUCCESS',
             JSON_OBJECT(
                 'before', JSON_OBJECT(
                     'displayName', :beforeName,
                     'departmentId', :beforeDepartmentId,
                     'version', :beforeVersion
                 ),
                 'after', JSON_OBJECT(
                     'displayName', :afterName,
                     'departmentId', :afterDepartmentId,
                     'version', :afterVersion
                 )
             ))
        """)
                .setParameter("actorId", actorId)
                .setParameter("userId", userId)
                .setParameter("beforeName", beforeName)
                .setParameter("afterName", afterName)
                .setParameter("beforeDepartmentId", beforeDepartmentId)
                .setParameter("afterDepartmentId", afterDepartmentId)
                .setParameter("beforeVersion", beforeVersion)
                .setParameter("afterVersion", afterVersion)
                .executeUpdate();
    }

    public void recordUserRolesChanged(
            Long actorId,
            Long userId,
            String beforeRoles,
            String afterRoles,
            Long beforeVersion,
            Long afterVersion) {

        entityManager.createNativeQuery("""
        INSERT INTO operation_log
            (actor_id, log_category, action,
             target_type, target_id, result, details)
        VALUES
            (:actorId, 'ADMIN', 'USER_ROLES_CHANGE',
             'APP_USER', :userId, 'SUCCESS',
             JSON_OBJECT(
                 'beforeRoles', :beforeRoles,
                 'afterRoles', :afterRoles,
                 'beforeVersion', :beforeVersion,
                 'afterVersion', :afterVersion
             ))
        """)
                .setParameter("actorId", actorId)
                .setParameter("userId", userId)
                .setParameter("beforeRoles", beforeRoles)
                .setParameter("afterRoles", afterRoles)
                .setParameter("beforeVersion", beforeVersion)
                .setParameter("afterVersion", afterVersion)
                .executeUpdate();
    }

    public void recordDepartmentChanged(
            Long actorId,
            Long departmentId,
            boolean created,
            String beforeName,
            String afterName,
            Long beforeVersion,
            Long afterVersion) {

        entityManager.createNativeQuery("""
        INSERT INTO operation_log
            (actor_id, log_category, action,
             target_type, target_id, result, details)
        VALUES
            (:actorId, 'ADMIN', :action,
             'DEPARTMENT', :departmentId, 'SUCCESS',
             JSON_OBJECT(
                 'beforeName', :beforeName,
                 'afterName', :afterName,
                 'beforeVersion', :beforeVersion,
                 'afterVersion', :afterVersion
             ))
        """)
                .setParameter("actorId", actorId)
                .setParameter("departmentId", departmentId)
                .setParameter("action",
                        created ? "DEPARTMENT_CREATE" : "DEPARTMENT_UPDATE")
                .setParameter("beforeName", beforeName)
                .setParameter("afterName", afterName)
                .setParameter("beforeVersion", beforeVersion)
                .setParameter("afterVersion", afterVersion)
                .executeUpdate();
    }

    public void recordCategoryChanged(
            Long actorId,
            Long categoryId,
            boolean created,
            String beforeName,
            String afterName,
            Long beforeVersion,
            Long afterVersion) {

        entityManager.createNativeQuery("""
        INSERT INTO operation_log
            (actor_id, log_category, action,
             target_type, target_id, result, details)
        VALUES
            (:actorId, 'ADMIN', :action,
             'ASSET_CATEGORY', :categoryId, 'SUCCESS',
             JSON_OBJECT(
                 'beforeName', :beforeName,
                 'afterName', :afterName,
                 'beforeVersion', :beforeVersion,
                 'afterVersion', :afterVersion
             ))
        """)
                .setParameter("actorId", actorId)
                .setParameter("categoryId", categoryId)
                .setParameter("action",
                        created ? "CATEGORY_CREATE" : "CATEGORY_UPDATE")
                .setParameter("beforeName", beforeName)
                .setParameter("afterName", afterName)
                .setParameter("beforeVersion", beforeVersion)
                .setParameter("afterVersion", afterVersion)
                .executeUpdate();
    }


}