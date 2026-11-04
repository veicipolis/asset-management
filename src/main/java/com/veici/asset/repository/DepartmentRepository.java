package com.veici.asset.repository;

import com.veici.asset.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepartmentRepository
        extends JpaRepository<Department, Long> {

    List<Department> findAllByOrderByNameAsc();
}