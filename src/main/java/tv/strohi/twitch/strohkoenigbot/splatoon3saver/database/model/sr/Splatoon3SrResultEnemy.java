package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.id.ResultEnemyId;

import javax.persistence.*;

@Entity(name = "splatoon_3_sr_result_enemy")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)@IdClass(ResultEnemyId.class)
public class Splatoon3SrResultEnemy {
	@Id
	@Column(name = "result_id")
	private long resultId;

	@Id
	@Column(name = "enemy_id")
	private long enemyId;

	private Integer spawnCount;

	private Integer teamDestroyCount;

	private Integer ownDestroyCount;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "result_id", nullable = false, insertable = false, updatable = false)
	private Splatoon3SrResult result;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "enemy_id", nullable = false, insertable = false, updatable = false)
	private Splatoon3SrEnemy enemy;
}


//@Entity
//@Table(name = "Employee")
//@IdClass(EmployeeId.class)
//public class Employee {
//
//	@Id
//	@Column(name = "Name")
//	private String name;
//	@Id
//	@Column(name = "Department")
//	private String departmentName;
//	@Column(name = "Designation")
//	private String designation;
//	@Id
//	@Column(name = "DepartmentLocation")
//	private String departmentLocation;
//	@ManyToOne
//	@JoinColumns({
//		@JoinColumn(name = "Department", referencedColumnName = "Name", insertable = false, updatable = false),
//		@JoinColumn(name = "DepartmentLocation", referencedColumnName = "Location", insertable = false, updatable = false),
//	})
//	@JsonIgnore
//	private Department department;
//	@Column(name = "Salary")
//	private Double salary;
//}
