// Admins create content. Managers also assign training; viewers see results; certifiers manage certificates.
export const AUTHOR_ROLES = ['SUPER_ADMIN', 'ORG_ADMIN'];
const MANAGER_ROLES = ['SUPER_ADMIN', 'ORG_ADMIN', 'LND_MANAGER'];
const VIEWER_ROLES = ['SUPER_ADMIN', 'ORG_ADMIN', 'LND_MANAGER', 'TRAINER', 'EXAMINER'];
const CERTIFIER_ROLES = ['SUPER_ADMIN', 'ORG_ADMIN', 'CERTIFICATION_AUTHORITY'];

export const isAdmin = (user) => AUTHOR_ROLES.includes(user?.role);
export const isManager = (user) => MANAGER_ROLES.includes(user?.role);
export const isViewer = (user) => VIEWER_ROLES.includes(user?.role);
export const isCertifier = (user) => CERTIFIER_ROLES.includes(user?.role);
export const isLearner = (user) => user?.role === 'LEARNER';
