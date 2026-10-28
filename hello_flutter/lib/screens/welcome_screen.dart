// ignore_for_file: slash_for_doc_comments

import 'package:flutter/material.dart';

/**
 * Brand color tokens.
 * <p>
 * Product-specific colors for the welcome screen.
 *
 * Official reference:
 * ThemeData: https://api.flutter.dev/flutter/material/ThemeData-class.html
 */
const _bg = Color(0xFFF5EDE0);
const _nav = Color(0xFFFFFCF7);
const _dark = Color(0xFF3D1A0E);
const _brown = Color(0xFF7B3B2A);
const _gold = Color(0xFFA0704A);
const _body = Color(0xFF9A7A60);
const _list = Color(0xFF7A5A40);
const _muted = Color(0xFFB09070);
const _card = Color(0xFFFFFCF7);
const _iconBg = Color(0xFFF0E6D6);
const _warn = Color(0xFFE07B39);

/**
 * Welcome screen.
 * <p>
 * Landing page with hero, features, and stats.
 *
 * Official references:
 * Theme: https://api.flutter.dev/flutter/material/Theme-class.html
 * ThemeData.from: https://api.flutter.dev/flutter/material/ThemeData/ThemeData.from.html
 * Scaffold: https://api.flutter.dev/flutter/material/Scaffold-class.html
 * SafeArea: https://api.flutter.dev/flutter/widgets/SafeArea-class.html
 * CustomScrollView: https://api.flutter.dev/flutter/widgets/CustomScrollView-class.html
 *
 * Open-source reference:
 * material_3_demo: https://github.com/flutter/samples/tree/main/material_3_demo
 */
class WelcomePage extends StatelessWidget {
  const WelcomePage({super.key});

  /**
   * Local page theme.
   * <p>
   * Applies the SmartFridge palette to the landing page.
   *
   * Official references:
   * ThemeData: https://api.flutter.dev/flutter/material/ThemeData-class.html
   * ThemeData.from: https://api.flutter.dev/flutter/material/ThemeData/ThemeData.from.html
   * cardTheme: https://api.flutter.dev/flutter/material/ThemeData/cardTheme.html
   */
  ThemeData _theme(BuildContext context) {
    final scheme =
        ColorScheme.fromSeed(
          seedColor: _brown,
          brightness: Brightness.light,
        ).copyWith(
          primary: _brown,
          onPrimary: _nav,
          secondary: _gold,
          surface: _bg,
          onSurface: _dark,
          onSurfaceVariant: _body,
          outlineVariant: _iconBg,
        );

    return ThemeData.from(colorScheme: scheme, useMaterial3: true).copyWith(
      scaffoldBackgroundColor: _bg,
      canvasColor: _bg,
      cardTheme: CardThemeData(
        color: _card,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: _dark,
          foregroundColor: _nav,
          padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 16),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(30),
          ),
          textStyle: const TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w700,
            letterSpacing: 0.2,
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: _dark,
          side: const BorderSide(color: _brown, width: 1.8),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(30),
          ),
          textStyle: const TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w700,
            letterSpacing: 0.2,
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Theme(
      data: _theme(context),
      child: Scaffold(
        body: SafeArea(
          child: CustomScrollView(
            slivers: const [
              SliverToBoxAdapter(child: _NavBar()),
              SliverToBoxAdapter(child: _HeroSection()),
              SliverToBoxAdapter(child: _FeaturesSection()),
              SliverToBoxAdapter(child: _StatsBar()),
            ],
          ),
        ),
      ),
    );
  }
}

/**
 * Shared page frame.
 * <p>
 * Adds horizontal padding and a centered max width.
 *
 * Official references:
 * Padding: https://api.flutter.dev/flutter/widgets/Padding-class.html
 * Center: https://api.flutter.dev/flutter/widgets/Center-class.html
 * ConstrainedBox: https://api.flutter.dev/flutter/widgets/ConstrainedBox-class.html
 */
class _PageFrame extends StatelessWidget {
  const _PageFrame({
    required this.child,
    this.padding = const EdgeInsets.symmetric(horizontal: 24),
  });

  final Widget child;
  final EdgeInsets padding;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: padding,
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 1200),
          child: child,
        ),
      ),
    );
  }
}

/**
 * Top navigation bar.
 * <p>
 * Responsive marketing navigation built from standard widgets.
 *
 * Official references:
 * LayoutBuilder: https://api.flutter.dev/flutter/widgets/LayoutBuilder-class.html
 * Row: https://api.flutter.dev/flutter/widgets/Row-class.html
 * TextButton: https://api.flutter.dev/flutter/material/TextButton-class.html
 * FilledButton: https://api.flutter.dev/flutter/material/FilledButton-class.html
 */
class _NavBar extends StatelessWidget {
  const _NavBar();

  @override
  Widget build(BuildContext context) {
    return Container(
      color: _nav,
      child: _PageFrame(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final wide = constraints.maxWidth > 980;

            return Row(
              children: [
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 28,
                      height: 28,
                      decoration: BoxDecoration(
                        color: _brown,
                        borderRadius: BorderRadius.circular(7),
                      ),
                      child: const Icon(Icons.kitchen, color: _nav, size: 14),
                    ),
                    const SizedBox(width: 8),
                    const Text(
                      'SmartFridge',
                      style: TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.w800,
                        color: _dark,
                      ),
                    ),
                  ],
                ),
                const Spacer(),
                if (wide) ...[
                  const _NavLink('HOME', active: true),
                  const _NavLink('FRIDGE'),
                  const _NavLink('RECIPES'),
                  const _NavLink('PRICING'),
                  const SizedBox(width: 28),
                  TextButton(
                    onPressed: () => Navigator.pushNamed(context, '/auth'),
                    style: TextButton.styleFrom(
                      foregroundColor: _dark,
                      textStyle: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    child: const Text('Sign In'),
                  ),
                  const SizedBox(width: 10),
                ],
                FilledButton(
                  style: FilledButton.styleFrom(
                    backgroundColor: _brown,
                    foregroundColor: _nav,
                    padding: const EdgeInsets.symmetric(
                      horizontal: 20,
                      vertical: 10,
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(24),
                    ),
                    textStyle: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  onPressed: () => Navigator.pushNamed(context, '/auth'),
                  child: const Text('LOG IN'),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}

/**
 * Navigation link.
 *
 * Official references:
 * Text: https://api.flutter.dev/flutter/widgets/Text-class.html
 * Padding: https://api.flutter.dev/flutter/widgets/Padding-class.html
 */
class _NavLink extends StatelessWidget {
  const _NavLink(this.label, {this.active = false});

  final String label;
  final bool active;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Text(
        label,
        style: TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w600,
          color: active ? _brown : _muted,
          decoration: active ? TextDecoration.underline : null,
          decorationColor: _brown,
          decorationThickness: 2,
        ),
      ),
    );
  }
}

/**
 * Hero section.
 * <p>
 * Responsive layout with text on the left and visuals on the right.
 *
 * Official references:
 * LayoutBuilder: https://api.flutter.dev/flutter/widgets/LayoutBuilder-class.html
 * Row: https://api.flutter.dev/flutter/widgets/Row-class.html
 * Column: https://api.flutter.dev/flutter/widgets/Column-class.html
 */
class _HeroSection extends StatelessWidget {
  const _HeroSection();

  @override
  Widget build(BuildContext context) {
    return _PageFrame(
      padding: const EdgeInsets.fromLTRB(24, 28, 24, 36),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final wide = constraints.maxWidth > 860;

          if (wide) {
            return Row(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: const [
                Expanded(
                  flex: 50,
                  child: Padding(
                    padding: EdgeInsets.fromLTRB(36, 20, 24, 28),
                    child: _HeroLeft(),
                  ),
                ),
                Expanded(
                  flex: 50,
                  child: Padding(
                    padding: EdgeInsets.only(left: 12),
                    child: _HeroRight(),
                  ),
                ),
              ],
            );
          }

          return const Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [_HeroLeft(), SizedBox(height: 36), _HeroRight()],
          );
        },
      ),
    );
  }
}

/**
 * Hero left content.
 * <p>
 * Headline, supporting text, checks, and actions.
 *
 * Official references:
 * ConstrainedBox: https://api.flutter.dev/flutter/widgets/ConstrainedBox-class.html
 * OverflowBar: https://api.flutter.dev/flutter/widgets/OverflowBar-class.html
 * FilledButton: https://api.flutter.dev/flutter/material/FilledButton-class.html
 * OutlinedButton: https://api.flutter.dev/flutter/material/OutlinedButton-class.html
 */
class _HeroLeft extends StatelessWidget {
  const _HeroLeft();

  static const _checks = [
    'Track ingredients & expiry dates automatically',
    'Get personalised recipe suggestions daily',
    "Build grocery lists from what's missing",
  ];

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'YOUR FRIDGE, SMARTER',
          style: TextStyle(
            fontSize: 11,
            fontWeight: FontWeight.w700,
            color: _gold,
            letterSpacing: 1.3,
          ),
        ),
        const SizedBox(height: 12),
        const Text(
          'Never Let Food',
          style: TextStyle(
            fontSize: 44,
            fontWeight: FontWeight.w900,
            color: _dark,
            height: 1,
            letterSpacing: -0.8,
          ),
        ),
        const Text(
          'Go to Waste',
          style: TextStyle(
            fontSize: 44,
            fontWeight: FontWeight.w900,
            color: _gold,
            height: 1,
            letterSpacing: -0.8,
          ),
        ),
        const SizedBox(height: 16),
        ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 560),
          child: const Text(
            'SmartFridge tracks every item in your fridge, alerts you before things expire, and suggests recipes from what you already have — saving money and reducing waste.',
            style: TextStyle(fontSize: 14, color: _body, height: 1.65),
          ),
        ),
        const SizedBox(height: 16),
        for (final item in _checks) ...[
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.only(top: 2),
                child: Container(
                  width: 16,
                  height: 16,
                  decoration: const BoxDecoration(
                    color: _dark,
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.check, color: _nav, size: 10),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  item,
                  style: const TextStyle(
                    fontSize: 13,
                    color: _list,
                    height: 1.5,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
        ],
        const SizedBox(height: 22),
        OverflowBar(
          spacing: 12,
          overflowSpacing: 12,
          children: [
            FilledButton(
              onPressed: () => Navigator.pushNamed(context, '/register'),
              child: const Text('GET STARTED FREE'),
            ),
            OutlinedButton(onPressed: () {}, child: const Text('MORE DETAIL')),
          ],
        ),
        const SizedBox(height: 22),
        Row(
          children: [
            SizedBox(
              width: 64,
              height: 28,
              child: Stack(
                children: [
                  for (var i = 0; i < 3; i++)
                    Positioned(
                      left: i * 17.0,
                      child: Container(
                        width: 28,
                        height: 28,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: const [
                            Color(0xFFFFD4B2),
                            Color(0xFFB8D8B8),
                            Color(0xFFB8C8E8),
                          ][i],
                          border: Border.all(color: _nav, width: 1.5),
                        ),
                        child: Icon(
                          Icons.person,
                          size: 13,
                          color: _dark.withValues(alpha: 0.4),
                        ),
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(width: 10),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    for (var i = 0; i < 4; i++)
                      const Icon(Icons.star, size: 12, color: _gold),
                    const Icon(Icons.star_half, size: 12, color: _gold),
                  ],
                ),
                const SizedBox(height: 2),
                const Text(
                  'Loved by 12,000+ households',
                  style: TextStyle(fontSize: 11, color: _muted),
                ),
              ],
            ),
          ],
        ),
      ],
    );
  }
}

/**
 * Hero right visual composition.
 * <p>
 * Hero right display built with a large circle, floating info cards, and positioned layout.
 * This is a custom visual composition, not a single official widget.
 *
 * Official references:
 * AspectRatio: https://api.flutter.dev/flutter/widgets/AspectRatio-class.html
 * LayoutBuilder: https://api.flutter.dev/flutter/widgets/LayoutBuilder-class.html
 * Stack: https://api.flutter.dev/flutter/widgets/Stack-class.html
 * Positioned: https://api.flutter.dev/flutter/widgets/Positioned-class.html
 *
 * Open-source reference:
 * material_3_demo: https://github.com/flutter/samples/tree/main/material_3_demo
 */
class _HeroRight extends StatelessWidget {
  const _HeroRight();

  @override
  Widget build(BuildContext context) {
    return AspectRatio(
      aspectRatio: 1.18,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final w = constraints.maxWidth;
          final h = constraints.maxHeight;
          final base = w < h ? w : h;
          final outerSize = base * 1.08;
          final innerSize = outerSize * 0.97;

          return Transform.translate(
            offset: Offset(w * 0.10, 0),
            child: Stack(
              clipBehavior: Clip.none,
              children: [
                Align(
                  alignment: Alignment.center,
                  child: Transform.translate(
                    offset: Offset(-w * 0.16, h * 0.06),
                    child: SizedBox(
                      width: outerSize * 1.12,
                      height: outerSize * 1.12,
                      child: Stack(
                        alignment: Alignment.center,
                        children: [
                          Container(
                            width: outerSize * 1.20,
                            height: outerSize * 1.20,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              border: Border.all(
                                color: const Color(
                                  0xFFC89A6B,
                                ).withValues(alpha: 0.18),
                                width: 1.5,
                              ),
                            ),
                          ),
                          Container(
                            width: outerSize,
                            height: outerSize,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: const Color(
                                0xFFC89A6B,
                              ).withValues(alpha: 0.20),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
                Align(
                  alignment: Alignment.center,
                  child: Transform.translate(
                    offset: Offset(-w * 0.02, h * 0.06),
                    child: Container(
                      width: innerSize,
                      height: innerSize,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withValues(alpha: 0.08),
                            blurRadius: 24,
                            offset: const Offset(0, 12),
                          ),
                        ],
                      ),
                      child: ClipOval(
                        child: Image.asset(
                          'assets/images/food.png',
                          fit: BoxFit.cover,
                          alignment: Alignment.center,
                        ),
                      ),
                    ),
                  ),
                ),
                Positioned(
                  top: h * 0.10,
                  right: w * 0.03,
                  child: Transform.scale(
                    scale: 1.10,
                    alignment: Alignment.center,
                    child: const _FloatCard(
                      icon: Icons.qr_code_scanner,
                      label: 'Scan & Track',
                      sub: 'Barcode + manual entry',
                    ),
                  ),
                ),
                Positioned(
                  top: h * 0.46,
                  right: w * 0.00,
                  child: Transform.scale(
                    scale: 1.10,
                    alignment: Alignment.center,
                    child: const _FloatCard(
                      icon: Icons.inventory_2_outlined,
                      label: '12 items expiring',
                      sub: 'Next 3 days · act now',
                      iconColor: _warn,
                    ),
                  ),
                ),
                Positioned(
                  bottom: h * 0.09,
                  right: w * 0.04,
                  child: Transform.scale(
                    scale: 1.10,
                    alignment: Alignment.center,
                    child: const _FloatCard(
                      icon: Icons.restaurant_menu,
                      label: '8 recipes ready',
                      sub: 'Based on your fridge',
                    ),
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}

/**
 * Floating hero card.
 * <p>
 * Small overlay card used inside the hero visual.
 *
 * Official references:
 * Card: https://api.flutter.dev/flutter/material/Card-class.html
 * Padding: https://api.flutter.dev/flutter/widgets/Padding-class.html
 */
class _FloatCard extends StatelessWidget {
  const _FloatCard({
    required this.icon,
    required this.label,
    required this.sub,
    this.iconColor = _dark,
  });

  final IconData icon;
  final String label;
  final String sub;
  final Color iconColor;

  @override
  Widget build(BuildContext context) {
    return Card(
      color: _card,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 34,
              height: 34,
              decoration: const BoxDecoration(
                color: _iconBg,
                shape: BoxShape.circle,
              ),
              child: Icon(icon, size: 16, color: iconColor),
            ),
            const SizedBox(width: 10),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: _dark,
                  ),
                ),
                const SizedBox(height: 2),
                Text(sub, style: const TextStyle(fontSize: 11, color: _muted)),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/**
 * Features section.
 * <p>
 * Responsive group of marketing feature cards.
 *
 * Official references:
 * LayoutBuilder: https://api.flutter.dev/flutter/widgets/LayoutBuilder-class.html
 * Row: https://api.flutter.dev/flutter/widgets/Row-class.html
 * Column: https://api.flutter.dev/flutter/widgets/Column-class.html
 * Card: https://api.flutter.dev/flutter/material/Card-class.html
 */
class _FeaturesSection extends StatelessWidget {
  const _FeaturesSection();

  @override
  Widget build(BuildContext context) {
    return _PageFrame(
      padding: const EdgeInsets.fromLTRB(24, 20, 24, 34),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final wide = constraints.maxWidth > 980;

          return Column(
            children: [
              const Text(
                'WHY SMARTFRIDGE?',
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                  color: _gold,
                  letterSpacing: 1.5,
                ),
              ),
              const SizedBox(height: 10),
              const Text(
                'Everything your kitchen needs',
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 22,
                  fontWeight: FontWeight.w900,
                  color: _dark,
                ),
              ),
              const SizedBox(height: 24),
              if (wide)
                const Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: _FeatureCard(
                        cardBg: _card,
                        iconBg: _iconBg,
                        icon: Icons.inventory_2_outlined,
                        iconColor: _dark,
                        title: 'Inventory Tracking',
                        titleColor: _dark,
                        body:
                            "Know exactly what's in your fridge at all times. Expiry alerts keep you ahead of waste.",
                        bodyColor: _body,
                      ),
                    ),
                    SizedBox(width: 16),
                    Expanded(
                      child: _FeatureCard(
                        cardBg: _brown,
                        iconBg: Colors.white,
                        icon: Icons.menu_book_outlined,
                        iconColor: _brown,
                        title: 'Recipe Suggestions',
                        titleColor: _nav,
                        body:
                            'Get personalised recipes every day based on what you already have at home.',
                        bodyColor: Color(0xCCFFFCF7),
                      ),
                    ),
                    SizedBox(width: 16),
                    Expanded(
                      child: _FeatureCard(
                        cardBg: _card,
                        iconBg: _iconBg,
                        icon: Icons.notifications_outlined,
                        iconColor: _dark,
                        title: 'Expiry Alerts',
                        titleColor: _dark,
                        body:
                            'Smart notifications remind you to use ingredients before they go bad.',
                        bodyColor: _body,
                      ),
                    ),
                  ],
                )
              else
                const Column(
                  children: [
                    _FeatureCard(
                      cardBg: _card,
                      iconBg: _iconBg,
                      icon: Icons.inventory_2_outlined,
                      iconColor: _dark,
                      title: 'Inventory Tracking',
                      titleColor: _dark,
                      body:
                          "Know exactly what's in your fridge at all times. Expiry alerts keep you ahead of waste.",
                      bodyColor: _body,
                    ),
                    SizedBox(height: 12),
                    _FeatureCard(
                      cardBg: _brown,
                      iconBg: Colors.white,
                      icon: Icons.menu_book_outlined,
                      iconColor: _brown,
                      title: 'Recipe Suggestions',
                      titleColor: _nav,
                      body:
                          'Get personalised recipes every day based on what you already have at home.',
                      bodyColor: Color(0xCCFFFCF7),
                    ),
                    SizedBox(height: 12),
                    _FeatureCard(
                      cardBg: _card,
                      iconBg: _iconBg,
                      icon: Icons.notifications_outlined,
                      iconColor: _dark,
                      title: 'Expiry Alerts',
                      titleColor: _dark,
                      body:
                          'Smart notifications remind you to use ingredients before they go bad.',
                      bodyColor: _body,
                    ),
                  ],
                ),
            ],
          );
        },
      ),
    );
  }
}

/**
 * Feature card.
 *
 * Official reference:
 * Card: https://api.flutter.dev/flutter/material/Card-class.html
 */
class _FeatureCard extends StatelessWidget {
  const _FeatureCard({
    required this.cardBg,
    required this.iconBg,
    required this.icon,
    required this.iconColor,
    required this.title,
    required this.titleColor,
    required this.body,
    required this.bodyColor,
  });

  final Color cardBg;
  final Color iconBg;
  final IconData icon;
  final Color iconColor;
  final String title;
  final Color titleColor;
  final String body;
  final Color bodyColor;

  @override
  Widget build(BuildContext context) {
    return Card(
      color: cardBg,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: iconBg,
                borderRadius: BorderRadius.circular(10),
              ),
              child: Icon(icon, color: iconColor, size: 20),
            ),
            const SizedBox(height: 12),
            Text(
              title,
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w800,
                color: titleColor,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              body,
              style: TextStyle(fontSize: 12, color: bodyColor, height: 1.6),
            ),
          ],
        ),
      ),
    );
  }
}

/**
 * Stats bar.
 * <p>
 * Responsive footer with product metrics.
 *
 * Official references:
 * LayoutBuilder: https://api.flutter.dev/flutter/widgets/LayoutBuilder-class.html
 * Row: https://api.flutter.dev/flutter/widgets/Row-class.html
 * Column: https://api.flutter.dev/flutter/widgets/Column-class.html
 */
class _StatsBar extends StatelessWidget {
  const _StatsBar();

  static const _stats = [
    ('12,000+', 'ACTIVE HOUSEHOLDS'),
    ('3M+', 'MEALS PLANNED'),
    (r'$340', 'AVG. ANNUAL SAVING'),
    ('68%', 'LESS FOOD WASTE'),
  ];

  @override
  Widget build(BuildContext context) {
    return Container(
      color: _dark,
      child: _PageFrame(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 24),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final wide = constraints.maxWidth > 980;

            return wide
                ? Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      for (final s in _stats)
                        _StatItem(value: s.$1, label: s.$2),
                    ],
                  )
                : Column(
                    children: [
                      for (int i = 0; i < _stats.length; i++) ...[
                        if (i > 0) const SizedBox(height: 20),
                        _StatItem(value: _stats[i].$1, label: _stats[i].$2),
                      ],
                    ],
                  );
          },
        ),
      ),
    );
  }
}

/**
 * Stat item.
 *
 * Official references:
 * Column: https://api.flutter.dev/flutter/widgets/Column-class.html
 * Text: https://api.flutter.dev/flutter/widgets/Text-class.html
 */
class _StatItem extends StatelessWidget {
  const _StatItem({required this.value, required this.label});

  final String value;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(
          value,
          style: const TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.w900,
            color: _gold,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          label,
          style: TextStyle(
            fontSize: 10,
            color: _nav.withValues(alpha: 0.7),
            letterSpacing: 1.2,
            fontWeight: FontWeight.w400,
          ),
        ),
      ],
    );
  }
}
