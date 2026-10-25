package com.learnplatform.repo;

import com.learnplatform.domain.Organisation;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganisationRepository extends JpaRepository<Organisation, UUID> {

}
