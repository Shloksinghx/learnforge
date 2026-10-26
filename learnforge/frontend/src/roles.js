// Only administrators create content (courses, lessons, questions, personas) and manage people.
export const AUTHOR_ROLES = ['SUPER_ADMIN', 'ORG_ADMIN'];
export const isAdmin = (user) => AUTHOR_ROLES.includes(user?.role);
