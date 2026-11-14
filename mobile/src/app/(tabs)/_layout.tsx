import { Tabs } from "expo-router";
import { useTranslation } from "react-i18next";
import { Icon, type IconName } from "../../components/Icon";
import { useApp } from "../../lib/state";

const TABS: { name: string; key: string; icon: IconName }[] = [
  { name: "index", key: "home", icon: "home" },
  { name: "live", key: "live", icon: "live" },
  { name: "tickets", key: "tickets", icon: "ticket" },
  { name: "account", key: "account", icon: "user" },
];

export default function TabLayout() {
  const { t } = useTranslation();
  const { theme } = useApp();
  return (
    <Tabs screenOptions={{
      headerShown: false,
      tabBarActiveTintColor: theme.text,
      tabBarInactiveTintColor: theme.muted,
      tabBarStyle: { backgroundColor: theme.card, borderTopColor: theme.border, height: 64, paddingTop: 6 },
      tabBarLabelStyle: { fontSize: 12, fontWeight: "600", marginBottom: 6 },
      sceneStyle: { backgroundColor: theme.bg },
    }}>
      {TABS.map((tab) => (
        <Tabs.Screen key={tab.name} name={tab.name} options={{
          title: t(`tabs.${tab.key}`),
          tabBarIcon: ({ color, focused }) => <Icon name={tab.icon} color={color as string} size={24} strokeWidth={focused ? 2.4 : 1.8} />,
        }} />
      ))}
    </Tabs>
  );
}
