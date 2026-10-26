/**
 * Smart Skill Match Calculation Utility
 * 
 * Computes a weighted match percentage between a Job's required skills
 * and a Candidate's verified skills.
 * 
 * Mandatory skills carry 2x weight over preferred/optional skills.
 */

export function calculateSkillMatch(jobSkills = [], candidateSkills = []) {
  if (!jobSkills || jobSkills.length === 0) {
    return {
      percentage: 100,
      matchedSkills: [],
      missingSkills: [],
      mandatoryMissing: [],
      rating: 'HIGH',
      badgeColor: '#10b981',
      badgeBg: 'rgba(16, 185, 129, 0.15)',
      badgeBorder: 'rgba(16, 185, 129, 0.35)',
      label: '100% Match (No skills required)'
    };
  }

  // Normalize candidate skill names to a Set of lowercase trimmed strings
  const candidateSkillSet = new Set(
    (candidateSkills || []).map(item => {
      if (typeof item === 'string') return item.trim().toLowerCase();
      if (item?.skill?.name) return item.skill.name.trim().toLowerCase();
      if (item?.name) return item.name.trim().toLowerCase();
      return '';
    }).filter(Boolean)
  );

  let totalWeight = 0;
  let matchedWeight = 0;
  const matchedSkills = [];
  const missingSkills = [];
  const mandatoryMissing = [];

  jobSkills.forEach(req => {
    const rawName = req?.skill?.name || (typeof req === 'string' ? req : req?.name || '');
    const normName = rawName.trim().toLowerCase();
    const isMandatory = req?.mandatory !== false; // defaults to mandatory if unspecified
    const weight = isMandatory ? 2.0 : 1.0;

    totalWeight += weight;

    if (candidateSkillSet.has(normName)) {
      matchedWeight += weight;
      matchedSkills.push({
        name: rawName,
        mandatory: isMandatory
      });
    } else {
      missingSkills.push({
        name: rawName,
        mandatory: isMandatory
      });
      if (isMandatory) {
        mandatoryMissing.push(rawName);
      }
    }
  });

  const percentage = totalWeight > 0 ? Math.round((matchedWeight / totalWeight) * 100) : 100;

  let rating = 'LOW';
  let badgeColor = '#94a3b8';
  let badgeBg = 'rgba(148, 163, 184, 0.12)';
  let badgeBorder = 'rgba(148, 163, 184, 0.25)';

  if (percentage >= 80) {
    rating = 'HIGH';
    badgeColor = '#10b981';
    badgeBg = 'rgba(16, 185, 129, 0.15)';
    badgeBorder = 'rgba(16, 185, 129, 0.4)';
  } else if (percentage >= 50) {
    rating = 'GOOD';
    badgeColor = '#f59e0b';
    badgeBg = 'rgba(245, 158, 11, 0.15)';
    badgeBorder = 'rgba(245, 158, 11, 0.35)';
  }

  return {
    percentage,
    matchedSkills,
    missingSkills,
    mandatoryMissing,
    rating,
    badgeColor,
    badgeBg,
    badgeBorder,
    label: `${percentage}% Match`
  };
}
